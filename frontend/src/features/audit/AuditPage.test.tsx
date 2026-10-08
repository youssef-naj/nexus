import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, orgDetail, pageOf, renderOrgRoutes, requestedUrls } from "@/test/orgFixtures"
import type { OrgRole } from "@/features/organizations/schemas"
import { AuditPage } from "./AuditPage"

afterEach(() => vi.unstubAllGlobals())

const auditKey = `GET /orgs/${ORG_ID}/audit`

function entry(overrides: Record<string, unknown> = {}) {
  return {
    id: "a-1",
    eventType: "ORGANIZATION_CREATED",
    actorUserId: "u-1",
    actorName: "Alice",
    targetType: "ORGANIZATION",
    targetId: "o-1",
    metadata: { name: "Acme", slug: "acme" },
    occurredAt: "2026-10-02T10:00:00Z",
    ...overrides,
  }
}

function render(
  role: OrgRole = "OWNER",
  extra: Record<string, () => Response> = {},
  items = [entry()],
) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [auditKey]: () => jsonResponse(200, pageOf(items)),
    ...extra,
  })
  renderOrgRoutes(<Route path="audit" element={<AuditPage />} />, `/orgs/${ORG_ID}/audit`)
  return fetchMock
}

const lastUrl = (fetchMock: ReturnType<typeof mockApi>) =>
  requestedUrls(fetchMock, "/audit?").at(-1) ?? ""

describe("AuditPage", () => {
  it("lists events with readable labels, the actor and a summary of the details", async () => {
    render("OWNER", {}, [
      entry(),
      entry({
        id: "a-2",
        eventType: "MEMBER_ROLE_CHANGED",
        actorName: null,
        metadata: { fromRole: "EMPLOYEE", toRole: "MANAGER" },
      }),
    ])

    // The "Event type" filter lists the same labels as the table, so a plain
    // getByText also matches its <option>. findByRole waits for the rows, and
    // within(table) keeps the filter options out of every assertion below.
    const rows = within(await screen.findByRole("table"))
    expect(rows.getByText("Organization created")).toBeInTheDocument()
    expect(rows.getByText("Alice")).toBeInTheDocument()
    expect(rows.getByText("Name: Acme · Slug: acme")).toBeInTheDocument()
    expect(rows.getByText("Member role changed")).toBeInTheDocument()
    expect(rows.getByText("System")).toBeInTheDocument()
    expect(rows.getByText("Role: Employee → Manager")).toBeInTheDocument()
  })

  it("still shows an event type this client does not know", async () => {
    render("OWNER", {}, [entry({ eventType: "SOMETHING_NEW_HAPPENED", metadata: {} })])

    expect(await screen.findByText("Something new happened")).toBeInTheDocument()
  })

  it("sends the filters as query parameters and goes back to the first page", async () => {
    const fetchMock = render()
    const user = userEvent.setup()
    await screen.findByRole("table")

    await user.selectOptions(screen.getByLabelText("Event type"), "REQUEST_APPROVED")
    await user.type(screen.getByLabelText("From date"), "2026-10-01")
    await user.type(screen.getByLabelText("To date"), "2026-10-31")

    await waitFor(() => {
      const url = lastUrl(fetchMock)
      expect(url).toContain("eventType=REQUEST_APPROVED")
      expect(url).toContain("from=2026-10-01")
      expect(url).toContain("to=2026-10-31")
      expect(url).toContain("page=0")
    })
  })

  it("moves between pages", async () => {
    const fetchMock = render("OWNER", {
      [auditKey]: () => jsonResponse(200, pageOf([entry()], { totalElements: 25, totalPages: 2 })),
    })
    const user = userEvent.setup()

    expect(await screen.findByText("Page 1 of 2")).toBeInTheDocument()
    await user.click(screen.getByRole("button", { name: "Next page" }))

    await waitFor(() => expect(lastUrl(fetchMock)).toContain("page=1"))
  })

  it("explains an empty log differently when filters are active", async () => {
    render("OWNER", {}, [])
    expect(await screen.findByText("No audit events yet.")).toBeInTheDocument()

    await userEvent.setup().selectOptions(screen.getByLabelText("Event type"), "MEMBER_LEFT")

    expect(await screen.findByText("No events match these filters.")).toBeInTheDocument()
  })

  it("explains a missing permission instead of showing an error", async () => {
    render("MANAGER", { [auditKey]: () => jsonResponse(403, { status: 403 }) })

    expect(
      await screen.findByText("You don't have permission to view the audit log."),
    ).toBeInTheDocument()
    expect(screen.queryByText("Couldn't load the audit log.")).not.toBeInTheDocument()
  })

  it("shows an error with a retry", async () => {
    let calls = 0
    render("OWNER", {
      [auditKey]: () =>
        ++calls === 1 ? jsonResponse(500, { status: 500 }) : jsonResponse(200, pageOf([entry()])),
    })

    expect(await screen.findByText("Couldn't load the audit log.")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }))
    expect(
      within(await screen.findByRole("table")).getByText("Organization created"),
    ).toBeInTheDocument()
  })
})
