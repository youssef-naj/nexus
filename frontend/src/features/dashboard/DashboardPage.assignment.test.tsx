import { screen } from "@testing-library/react"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { OrgLayout } from "@/features/organizations/OrgLayout"
import type { OrgRole } from "@/features/organizations/schemas"
import { ORG_ID, orgDetail } from "@/test/orgFixtures"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { DashboardPage } from "./DashboardPage"

afterEach(() => vi.unstubAllGlobals())

const zero = { DRAFT: 0, SUBMITTED: 0, CHANGES_REQUESTED: 0, APPROVED: 0, REJECTED: 0 }
const requestsPath = `/orgs/${ORG_ID}/requests`

function dashboard(overrides: Record<string, unknown> = {}) {
  return {
    scope: "ORGANIZATION",
    total: 5,
    byStatus: { ...zero, SUBMITTED: 5 },
    awaitingReview: 5,
    assignedToMe: 4,
    mine: { total: 0, byStatus: zero },
    recent: [],
    ...overrides,
  }
}

function render(role: OrgRole, body: unknown) {
  mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [`GET /orgs/${ORG_ID}/dashboard`]: () => jsonResponse(200, body),
  })
  renderWithProviders(
    <Routes>
      <Route path="/orgs/:orgId" element={<OrgLayout />}>
        <Route index element={<DashboardPage />} />
      </Route>
    </Routes>,
    [`/orgs/${ORG_ID}`],
  )
}

describe("Dashboard assignment card", () => {
  it("shows reviewers what is assigned to them next to what awaits review", async () => {
    render("MANAGER", dashboard())

    expect(await screen.findByRole("heading", { name: "Assigned to you" })).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "4" })).toHaveAttribute(
      "href",
      `${requestsPath}?assignedToMe=true`,
    )
    expect(screen.getByRole("link", { name: "5" })).toHaveAttribute(
      "href",
      `${requestsPath}?reviewable=true`,
    )
    expect(screen.getByText(/requests assigned to you/)).toBeInTheDocument()
  })

  it("says so when nothing is assigned", async () => {
    render("MANAGER", dashboard({ assignedToMe: 0 }))

    expect(await screen.findByText("Nothing is assigned to you.")).toBeInTheDocument()
  })

  it("shows no assignment card to people who cannot review", async () => {
    render("EMPLOYEE", dashboard({ scope: "MINE", awaitingReview: null, assignedToMe: null }))

    await screen.findByRole("heading", { name: "Your requests" })
    expect(screen.queryByText("Assigned to you")).not.toBeInTheDocument()
    expect(screen.queryByText("Awaiting your review")).not.toBeInTheDocument()
  })

  it("describes assignment events in recent activity", async () => {
    render(
      "MANAGER",
      dashboard({
        recent: [
          {
            id: "e-1",
            requestId: "r-1",
            reference: "REQ-000007",
            title: "Desk",
            action: "ASSIGN",
            toStatus: "SUBMITTED",
            actorName: "Dan",
            targetName: "Ann",
            occurredAt: "2026-10-09T10:00:00Z",
          },
        ],
      }),
    )

    expect(await screen.findByText(/assigned the request to Ann/)).toBeInTheDocument()
  })
})
