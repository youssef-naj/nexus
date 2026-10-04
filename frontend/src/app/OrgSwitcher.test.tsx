import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes, useParams } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { OrgSwitcher } from "./OrgSwitcher"

afterEach(() => vi.unstubAllGlobals())

function Probe() {
  const { orgId } = useParams()
  return <p>Viewing {orgId}</p>
}

const organizations = [
  { id: "a-1", name: "Alpha Ltd", slug: "alpha", role: "OWNER", status: "ACTIVE" },
  { id: "b-2", name: "Beta Inc", slug: "beta", role: "EMPLOYEE", status: "ACTIVE" },
]

describe("OrgSwitcher", () => {
  it("names the current organization and switches to another one", async () => {
    mockApi({ "GET /orgs": () => jsonResponse(200, organizations) })
    renderWithProviders(
      <>
        <OrgSwitcher />
        <Routes>
          <Route path="/orgs/:orgId" element={<Probe />} />
        </Routes>
      </>,
      ["/orgs/a-1"],
    )
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Alpha Ltd" }))
    await user.click(await screen.findByRole("menuitem", { name: /Beta Inc/ }))

    expect(await screen.findByText("Viewing b-2")).toBeInTheDocument()
  })

  it("always offers to create an organization", async () => {
    mockApi({ "GET /orgs": () => jsonResponse(200, []) })
    renderWithProviders(<OrgSwitcher />)

    await userEvent.setup().click(await screen.findByRole("button", { name: "Organizations" }))

    expect(await screen.findByRole("menuitem", { name: "Create organization" })).toHaveAttribute(
      "href",
      "/organizations/new",
    )
  })
})
