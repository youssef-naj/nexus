import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { pageOf, requestedUrls } from "@/test/orgFixtures"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { PlatformAuditPage } from "./PlatformAuditPage"

afterEach(() => vi.unstubAllGlobals())

const auditKey = "GET /platform/audit"

function entry(overrides: Record<string, unknown> = {}) {
  return {
    id: "a-1",
    eventType: "PLATFORM_ORGANIZATION_SUSPENDED",
    actorName: "Pat",
    targetType: "ORGANIZATION",
    targetName: "Acme Corp",
    metadata: { reason: "NON_PAYMENT" },
    occurredAt: "2026-10-02T10:00:00Z",
    ...overrides,
  }
}

function render(extra: Record<string, () => Response> = {}, items = [entry()]) {
  const fetchMock = mockApi({ [auditKey]: () => jsonResponse(200, pageOf(items)), ...extra })
  renderWithProviders(
    <Routes>
      <Route path="/platform/audit" element={<PlatformAuditPage />} />
    </Routes>,
    ["/platform/audit"],
  )
  return fetchMock
}

const lastUrl = (fetchMock: ReturnType<typeof mockApi>) =>
  requestedUrls(fetchMock, "/platform/audit?").at(-1) ?? ""

/** The filter dropdown uses the same event names as the table, so look inside the table region. */
async function table() {
  return screen.findByRole("region", { name: "Platform audit events" })
}

describe("PlatformAuditPage", () => {
  it("lists platform events with who acted, what it concerned and why", async () => {
    render({}, [
      entry(),
      entry({
        id: "a-2",
        eventType: "PLATFORM_ADMIN_GRANTED",
        actorName: null,
        targetType: "USER",
        targetName: "Pat",
        metadata: {},
      }),
    ])

    const region = await table()
    expect(within(region).getByText("Organization suspended")).toBeInTheDocument()
    expect(within(region).getByText("Acme Corp")).toBeInTheDocument()
    expect(within(region).getByText("Reason: Non-payment")).toBeInTheDocument()
    expect(within(region).getByText("Platform administrator added")).toBeInTheDocument()
    expect(within(region).getByText("System")).toBeInTheDocument()
  })

  it("offers only platform event types in its filter and sends the choice", async () => {
    const fetchMock = render()
    const user = userEvent.setup()
    await table()

    const options = screen.getAllByRole("option").map((option) => option.textContent)
    expect(options).toContain("Platform administrator removed")
    expect(options).not.toContain("Request approved")
    await user.selectOptions(screen.getByLabelText("Event type"), "PLATFORM_ADMIN_REVOKED")

    await waitFor(() => expect(lastUrl(fetchMock)).toContain("eventType=PLATFORM_ADMIN_REVOKED"))
  })

  it("moves between pages", async () => {
    const fetchMock = render({
      [auditKey]: () => jsonResponse(200, pageOf([entry()], { totalElements: 25, totalPages: 2 })),
    })

    expect(await screen.findByText("Page 1 of 2")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Next page" }))

    await waitFor(() => expect(lastUrl(fetchMock)).toContain("page=1"))
  })

  it("explains empty results and recovers from an error", async () => {
    let calls = 0
    render({
      [auditKey]: () =>
        ++calls === 1 ? jsonResponse(500, { status: 500 }) : jsonResponse(200, pageOf([])),
    })

    expect(await screen.findByText("Couldn't load the platform audit.")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }))
    expect(await screen.findByText("No platform events yet.")).toBeInTheDocument()
  })
})
