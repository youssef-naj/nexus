import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { Can } from "./Can"
import { OrgLayout } from "./OrgLayout"

afterEach(() => vi.unstubAllGlobals())

const ORG_ID = "11111111-1111-1111-1111-111111111111"

function detail(role: string, permissions: string[]) {
  return { id: ORG_ID, name: "Acme", slug: "acme", role, status: "ACTIVE", permissions }
}

function renderOrg() {
  renderWithProviders(
    <Routes>
      <Route path="/orgs/:orgId" element={<OrgLayout />}>
        <Route
          index
          element={
            <>
              <p>Inside the organization</p>
              <Can permission="MEMBER_INVITE">
                <button>Invite member</button>
              </Can>
            </>
          }
        />
      </Route>
    </Routes>,
    [`/orgs/${ORG_ID}`],
  )
}

describe("OrgLayout", () => {
  it("shows admin-only controls to a member who holds the permission", async () => {
    mockApi({
      [`GET /orgs/${ORG_ID}`]: () =>
        jsonResponse(200, detail("OWNER", ["ORGANIZATION_VIEW", "MEMBER_INVITE"])),
    })
    renderOrg()

    expect(await screen.findByText("Inside the organization")).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Invite member" })).toBeInTheDocument()
  })

  it("hides them from a member who does not", async () => {
    mockApi({
      [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, detail("EMPLOYEE", ["ORGANIZATION_VIEW"])),
    })
    renderOrg()

    expect(await screen.findByText("Inside the organization")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Invite member" })).not.toBeInTheDocument()
  })

  it("shows one neutral screen for an organization that is missing or off limits", async () => {
    mockApi({ [`GET /orgs/${ORG_ID}`]: () => jsonResponse(404, { status: 404 }) })
    renderOrg()

    expect(await screen.findByText("Organization not found")).toBeInTheDocument()
    expect(screen.queryByText("Inside the organization")).not.toBeInTheDocument()
  })

  it("explains a suspended organization to its members", async () => {
    mockApi({
      [`GET /orgs/${ORG_ID}`]: () =>
        jsonResponse(403, { status: 403, code: "ORGANIZATION_SUSPENDED" }),
    })
    renderOrg()

    expect(await screen.findByText("Organization suspended")).toBeInTheDocument()
  })

  it("offers a retry when the server fails", async () => {
    mockApi({ [`GET /orgs/${ORG_ID}`]: () => jsonResponse(500, { status: 500 }) })
    renderOrg()

    expect(await screen.findByText("Couldn't load the organization")).toBeInTheDocument()
    await userEvent.setup()
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument()
  })
})
