import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { OrganizationsPage } from "./OrganizationsPage"

afterEach(() => vi.unstubAllGlobals())

const alpha = { id: "a-1", name: "Alpha Ltd", slug: "alpha-ltd", role: "OWNER", status: "ACTIVE" }
const beta = {
  id: "b-2",
  name: "Beta Inc",
  slug: "beta-inc",
  role: "EMPLOYEE",
  status: "SUSPENDED",
}

function renderPage() {
  renderWithProviders(
    <Routes>
      <Route path="/" element={<OrganizationsPage />} />
      <Route path="/orgs/:orgId" element={<p>Org home</p>} />
      <Route path="/organizations/new" element={<p>Create page</p>} />
    </Routes>,
    ["/"],
  )
}

describe("OrganizationsPage", () => {
  it("lists the organizations with the user's role in each and flags suspended ones", async () => {
    mockApi({ "GET /orgs": () => jsonResponse(200, [alpha, beta]) })
    renderPage()

    expect(await screen.findByText("Alpha Ltd")).toBeInTheDocument()
    expect(screen.getByText("Beta Inc")).toBeInTheDocument()
    expect(screen.getByText("Owner")).toBeInTheDocument()
    expect(screen.getByText("Employee")).toBeInTheDocument()
    expect(screen.getByText("Suspended")).toBeInTheDocument()
  })

  it("opens the only organization directly", async () => {
    mockApi({ "GET /orgs": () => jsonResponse(200, [alpha]) })
    renderPage()

    expect(await screen.findByText("Org home")).toBeInTheDocument()
  })

  it("offers to create the first organization when there are none", async () => {
    mockApi({ "GET /orgs": () => jsonResponse(200, []) })
    renderPage()

    expect(await screen.findByText(/don't belong to any organization/)).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Create an organization" })).toHaveAttribute(
      "href",
      "/organizations/new",
    )
  })

  it("shows an error and recovers when the retry succeeds", async () => {
    let calls = 0
    mockApi({
      "GET /orgs": () =>
        ++calls === 1 ? jsonResponse(500, { status: 500 }) : jsonResponse(200, [alpha, beta]),
    })
    renderPage()

    expect(await screen.findByText("Couldn't load your organizations")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }))

    expect(await screen.findByText("Alpha Ltd")).toBeInTheDocument()
  })
})
