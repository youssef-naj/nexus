import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, memberJson, orgDetail, pageJson, renderOrgPage } from "@/test/orgFixtures"
import type { OrgRole } from "@/features/organizations/schemas"
import { MembersPage } from "./MembersPage"

afterEach(() => vi.unstubAllGlobals())

const invitationsKey = `GET /orgs/${ORG_ID}/invitations`

function routes(role: OrgRole, extra: Record<string, () => Response> = {}) {
  return {
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [`GET /orgs/${ORG_ID}/members?page=0&size=20`]: () =>
      jsonResponse(200, pageJson([memberJson({ id: "m-me", displayName: "Me", role, you: true })])),
    [invitationsKey]: () => jsonResponse(200, []),
    ...extra,
  }
}

const pending = {
  id: "inv-1",
  email: "guest@example.com",
  role: "MANAGER",
  createdAt: "2026-10-04T10:00:00Z",
  expiresAt: "2026-10-11T10:00:00Z",
  expired: false,
}

describe("Invitations section", () => {
  it("limits the roles to those the inviter may grant", async () => {
    mockApi(routes("ADMIN"))
    renderOrgPage(<MembersPage />)

    const roleSelect = await screen.findByLabelText("Role")
    expect(
      within(roleSelect)
        .getAllByRole("option")
        .map((o) => o.textContent),
    ).toEqual(["Manager", "Employee"])
  })

  it("offers an owner every role", async () => {
    mockApi(routes("OWNER"))
    renderOrgPage(<MembersPage />)

    const roleSelect = await screen.findByLabelText("Role")
    expect(within(roleSelect).getAllByRole("option")).toHaveLength(4)
  })

  it("validates the address in the browser", async () => {
    const fetchMock = mockApi(routes("ADMIN"))
    renderOrgPage(<MembersPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Email address"), "not-an-email")
    await user.click(screen.getByRole("button", { name: "Send invitation" }))

    expect(await screen.findByText("Enter a valid email address.")).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false)
  })

  it("sends an invitation and confirms it", async () => {
    const fetchMock = mockApi(
      routes("ADMIN", {
        [`POST /orgs/${ORG_ID}/invitations`]: () =>
          jsonResponse(201, { ...pending, email: "new@example.com" }),
      }),
    )
    renderOrgPage(<MembersPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Email address"), "  new@example.com ")
    await user.selectOptions(screen.getByLabelText("Role"), "MANAGER")
    await user.click(screen.getByRole("button", { name: "Send invitation" }))

    expect(await screen.findByText("Invitation sent to new@example.com.")).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST")?.[1]
    expect(JSON.parse(String(post?.body))).toEqual({ email: "new@example.com", role: "MANAGER" })
    expect(new Headers(post?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("explains when the person is already a member", async () => {
    mockApi(
      routes("ADMIN", {
        [`POST /orgs/${ORG_ID}/invitations`]: () =>
          jsonResponse(409, { status: 409, code: "ALREADY_MEMBER" }),
      }),
    )
    renderOrgPage(<MembersPage />)
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Email address"), "member@example.com")
    await user.click(screen.getByRole("button", { name: "Send invitation" }))

    expect(await screen.findByText("This person is already a member.")).toBeInTheDocument()
  })

  it("lists pending invitations, flags expired ones and revokes on request", async () => {
    let revoked = false
    const fetchMock = mockApi(
      routes("OWNER", {
        [invitationsKey]: () =>
          jsonResponse(
            200,
            revoked
              ? []
              : [pending, { ...pending, id: "inv-2", email: "late@example.com", expired: true }],
          ),
        [`DELETE /orgs/${ORG_ID}/invitations/inv-1`]: () => {
          revoked = true
          return jsonResponse(204)
        },
      }),
    )
    renderOrgPage(<MembersPage />)

    expect(await screen.findByText("guest@example.com")).toBeInTheDocument()
    expect(screen.getByText("Expired")).toBeInTheDocument()
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "Revoke invitation for guest@example.com" }))

    await waitFor(() => expect(screen.queryByText("guest@example.com")).not.toBeInTheDocument())
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(true)
  })

  it("hides revoke for invitations the admin could not have granted", async () => {
    mockApi(
      routes("ADMIN", {
        [invitationsKey]: () => jsonResponse(200, [{ ...pending, role: "OWNER" }]),
      }),
    )
    renderOrgPage(<MembersPage />)

    expect(await screen.findByText("guest@example.com")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /Revoke invitation/ })).not.toBeInTheDocument()
  })
})
