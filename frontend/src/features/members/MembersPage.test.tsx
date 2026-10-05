import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, memberJson, orgDetail, pageJson, renderOrgPage } from "@/test/orgFixtures"
import { MembersPage } from "./MembersPage"

afterEach(() => vi.unstubAllGlobals())

const detailKey = `GET /orgs/${ORG_ID}`
const listKey = `GET /orgs/${ORG_ID}/members?page=0&size=20`
const invitationsKey = `GET /orgs/${ORG_ID}/invitations`

const alice = memberJson({
  id: "m-alice",
  displayName: "Alice Owner",
  email: "alice@example.com",
  role: "OWNER",
})
const ann = memberJson({
  id: "m-ann",
  displayName: "Ann Admin",
  email: "ann@example.com",
  role: "ADMIN",
  you: true,
})
const carol = memberJson({
  id: "m-carol",
  displayName: "Carol Employee",
  email: "carol@example.com",
  role: "EMPLOYEE",
  version: 4,
})

function adminRoutes(extra: Record<string, () => Response> = {}) {
  return {
    [detailKey]: () => jsonResponse(200, orgDetail("ADMIN")),
    [listKey]: () => jsonResponse(200, pageJson([alice, ann, carol])),
    [invitationsKey]: () => jsonResponse(200, []),
    ...extra,
  }
}

describe("MembersPage", () => {
  it("shows an employee the team without email addresses, role controls or invitations", async () => {
    mockApi({
      [detailKey]: () => jsonResponse(200, orgDetail("EMPLOYEE")),
      [listKey]: () =>
        jsonResponse(
          200,
          pageJson([
            { ...alice, email: null },
            { ...carol, email: null, you: true },
          ]),
        ),
    })
    renderOrgPage(<MembersPage />)

    expect(await screen.findByText("Carol Employee")).toBeInTheDocument()
    expect(screen.queryByRole("columnheader", { name: "Email" })).not.toBeInTheDocument()
    expect(screen.queryByRole("combobox", { name: /Role of/ })).not.toBeInTheDocument()
    expect(screen.queryByText("Invite someone")).not.toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Leave organization" })).toBeInTheDocument()
  })

  it("offers an admin role controls only for members they may manage", async () => {
    mockApi(adminRoutes())
    renderOrgPage(<MembersPage />)

    const carolSelect = await screen.findByRole("combobox", { name: "Role of Carol Employee" })
    expect(screen.getByRole("columnheader", { name: "Email" })).toBeInTheDocument()
    expect(
      within(carolSelect)
        .getAllByRole("option")
        .map((o) => o.textContent),
    ).toEqual(["Manager", "Employee"])
    // The owner outranks the admin, and nobody manages themselves
    expect(screen.queryByRole("combobox", { name: "Role of Alice Owner" })).not.toBeInTheDocument()
    expect(screen.queryByRole("combobox", { name: "Role of Ann Admin" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Remove Alice Owner" })).not.toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Remove Carol Employee" })).toBeInTheDocument()
  })

  it("changes a role with the version the page saw, then reloads the list", async () => {
    let loads = 0
    const fetchMock = mockApi(
      adminRoutes({
        [listKey]: () => {
          loads += 1
          const updated = { ...carol, role: "MANAGER" as const, version: 5 }
          return jsonResponse(200, pageJson([alice, ann, loads === 1 ? carol : updated]))
        },
        [`PATCH /orgs/${ORG_ID}/members/m-carol`]: () =>
          jsonResponse(200, { id: "m-carol", role: "MANAGER", version: 5 }),
      }),
    )
    renderOrgPage(<MembersPage />)
    const user = userEvent.setup()

    await user.selectOptions(
      await screen.findByRole("combobox", { name: "Role of Carol Employee" }),
      "MANAGER",
    )

    await waitFor(() =>
      expect(screen.getByRole("combobox", { name: "Role of Carol Employee" })).toHaveValue(
        "MANAGER",
      ),
    )
    const patch = fetchMock.mock.calls.find(([, init]) => init?.method === "PATCH")?.[1]
    expect(JSON.parse(String(patch?.body))).toEqual({ role: "MANAGER", version: 4 })
    expect(new Headers(patch?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
    expect(loads).toBe(2)
  })

  it("explains a conflict and reloads when someone else changed the member first", async () => {
    let loads = 0
    mockApi(
      adminRoutes({
        [listKey]: () => {
          loads += 1
          return jsonResponse(200, pageJson([alice, ann, carol]))
        },
        [`PATCH /orgs/${ORG_ID}/members/m-carol`]: () =>
          jsonResponse(409, { status: 409, code: "STALE_VERSION" }),
      }),
    )
    renderOrgPage(<MembersPage />)

    await userEvent
      .setup()
      .selectOptions(
        await screen.findByRole("combobox", { name: "Role of Carol Employee" }),
        "MANAGER",
      )

    expect(await screen.findByText(/Someone else changed this member/)).toBeInTheDocument()
    await waitFor(() => expect(loads).toBe(2))
  })

  it("asks for confirmation before removing, and cancelling sends nothing", async () => {
    const fetchMock = mockApi(
      adminRoutes({
        [`DELETE /orgs/${ORG_ID}/members/m-carol`]: () => jsonResponse(204),
      }),
    )
    renderOrgPage(<MembersPage />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Remove Carol Employee" }))
    await user.click(screen.getByRole("button", { name: "Cancel" }))
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(false)

    await user.click(screen.getByRole("button", { name: "Remove Carol Employee" }))
    await user.click(screen.getByRole("button", { name: "Confirm removal of Carol Employee" }))

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(true),
    )
  })

  it("moves between pages", async () => {
    const fetchMock = mockApi({
      ...adminRoutes({
        [listKey]: () =>
          jsonResponse(200, pageJson([alice, ann], { totalElements: 3, totalPages: 2 })),
      }),
      [`GET /orgs/${ORG_ID}/members?page=1&size=20`]: () =>
        jsonResponse(200, pageJson([carol], { page: 1, totalElements: 3, totalPages: 2 })),
    })
    renderOrgPage(<MembersPage />)
    const user = userEvent.setup()

    expect(await screen.findByText("Page 1 of 2")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Previous page" })).toBeDisabled()
    await user.click(screen.getByRole("button", { name: "Next page" }))

    expect(await screen.findByText("Carol Employee")).toBeInTheDocument()
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith("page=1&size=20"))).toBe(true)
  })

  it("filters by role", async () => {
    const fetchMock = mockApi({
      ...adminRoutes(),
      [`GET /orgs/${ORG_ID}/members?page=0&size=20&role=EMPLOYEE`]: () =>
        jsonResponse(200, pageJson([carol])),
    })
    renderOrgPage(<MembersPage />)

    await userEvent
      .setup()
      .selectOptions(await screen.findByLabelText("Filter by role"), "EMPLOYEE")

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(([url]) => String(url).endsWith("size=20&role=EMPLOYEE")),
      ).toBe(true),
    )
  })

  it("lets a member leave and returns them home", async () => {
    mockApi({
      [detailKey]: () => jsonResponse(200, orgDetail("EMPLOYEE")),
      [listKey]: () => jsonResponse(200, pageJson([{ ...carol, you: true, email: null }])),
      [`POST /orgs/${ORG_ID}/leave`]: () => jsonResponse(204),
    })
    renderOrgPage(<MembersPage />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Leave organization" }))
    await user.click(screen.getByRole("button", { name: "Confirm leaving" }))

    expect(await screen.findByText("Home")).toBeInTheDocument()
  })

  it("explains why the last owner cannot leave", async () => {
    mockApi(
      adminRoutes({
        [detailKey]: () => jsonResponse(200, orgDetail("OWNER")),
        [`POST /orgs/${ORG_ID}/leave`]: () =>
          jsonResponse(409, { status: 409, code: "LAST_OWNER" }),
      }),
    )
    renderOrgPage(<MembersPage />)
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Leave organization" }))
    await user.click(screen.getByRole("button", { name: "Confirm leaving" }))

    expect(await screen.findByText(/must keep at least one owner/)).toBeInTheDocument()
    expect(screen.queryByText("Home")).not.toBeInTheDocument()
  })
})
