import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { AcceptInvitationPage } from "./AcceptInvitationPage"

afterEach(() => vi.unstubAllGlobals())

function renderAccept(search: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<p>Home</p>} />
      <Route path="/invitations/accept" element={<AcceptInvitationPage />} />
      <Route path="/orgs/:orgId" element={<p>Org home</p>} />
    </Routes>,
    [`/invitations/accept${search}`],
  )
}

const preview = { organizationName: "Acme Corp", role: "MANAGER" }

describe("AcceptInvitationPage", () => {
  it("explains an incomplete link without calling the server", () => {
    const fetchMock = mockApi({})
    renderAccept("")

    expect(screen.getByText("Incomplete link")).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it("shows what the invitation is for before anything is spent", async () => {
    const fetchMock = mockApi({ "POST /invitations/preview": () => jsonResponse(200, preview) })
    renderAccept("?token=abc123")

    expect(await screen.findByRole("heading", { name: "Join Acme Corp" })).toBeInTheDocument()
    expect(screen.getByText("Manager")).toBeInTheDocument()
    expect(fetchMock.mock.calls).toHaveLength(1)
    expect(JSON.parse(String(fetchMock.mock.calls[0]?.[1]?.body))).toEqual({ token: "abc123" })
  })

  it("joins the organization when accepted", async () => {
    const fetchMock = mockApi({
      "POST /invitations/preview": () => jsonResponse(200, preview),
      "POST /invitations/accept": () =>
        jsonResponse(200, {
          organizationId: "org-1",
          organizationName: "Acme Corp",
          role: "MANAGER",
        }),
    })
    renderAccept("?token=abc123")

    await userEvent.setup().click(await screen.findByRole("button", { name: "Accept invitation" }))

    expect(await screen.findByText("Org home")).toBeInTheDocument()
    const accept = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/accept"))?.[1]
    expect(new Headers(accept?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("declines the invitation", async () => {
    mockApi({
      "POST /invitations/preview": () => jsonResponse(200, preview),
      "POST /invitations/reject": () => jsonResponse(204),
    })
    renderAccept("?token=abc123")

    await userEvent.setup().click(await screen.findByRole("button", { name: "Decline" }))

    expect(await screen.findByText("Invitation declined")).toBeInTheDocument()
  })

  it("gives one generic message when the invitation cannot be used", async () => {
    mockApi({ "POST /invitations/preview": () => jsonResponse(400, { status: 400 }) })
    renderAccept("?token=wrong")

    expect(await screen.findByText("This invitation can't be used")).toBeInTheDocument()
    expect(screen.getByText(/different email address/)).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Accept invitation" })).not.toBeInTheDocument()
  })

  it("shows a server failure differently from an unusable invitation", async () => {
    mockApi({ "POST /invitations/preview": () => jsonResponse(500, { status: 500 }) })
    renderAccept("?token=abc123")

    expect(await screen.findByText("Couldn't check the invitation")).toBeInTheDocument()
  })
})
