import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { CreateOrganizationPage } from "./CreateOrganizationPage"

afterEach(() => vi.unstubAllGlobals())

function renderPage() {
  renderWithProviders(
    <Routes>
      <Route path="/organizations/new" element={<CreateOrganizationPage />} />
      <Route path="/orgs/:orgId" element={<p>Org home</p>} />
    </Routes>,
    ["/organizations/new"],
  )
}

describe("CreateOrganizationPage", () => {
  it("validates in the browser and sends nothing when the name is empty", async () => {
    const fetchMock = mockApi({})
    renderPage()

    await userEvent.setup().click(screen.getByRole("button", { name: "Create organization" }))

    expect(await screen.findByText("Enter a name for the organization.")).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("creates the organization with a trimmed name and opens it", async () => {
    const fetchMock = mockApi({
      "POST /orgs": () =>
        jsonResponse(201, {
          id: "new-1",
          name: "Acme Corp",
          slug: "acme-corp",
          role: "OWNER",
          status: "ACTIVE",
        }),
    })
    renderPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText("Organization name"), "  Acme Corp  ")
    await user.click(screen.getByRole("button", { name: "Create organization" }))

    expect(await screen.findByText("Org home")).toBeInTheDocument()
    const init = fetchMock.mock.calls[0]?.[1]
    expect(JSON.parse(String(init?.body))).toEqual({ name: "Acme Corp" })
    expect(new Headers(init?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("shows a server-side name error under the field", async () => {
    mockApi({
      "POST /orgs": () =>
        jsonResponse(400, {
          status: 400,
          errors: { name: ["must not contain control characters"] },
        }),
    })
    renderPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText("Organization name"), "Acme")
    await user.click(screen.getByRole("button", { name: "Create organization" }))

    expect(await screen.findByText("must not contain control characters")).toBeInTheDocument()
    expect(screen.queryByText("Org home")).not.toBeInTheDocument()
  })
})
