import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { OrgLayout } from "@/features/organizations/OrgLayout"
import type { OrgRole } from "@/features/organizations/schemas"
import { ORG_ID, orgDetail } from "@/test/orgFixtures"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { DashboardPage } from "./DashboardPage"

afterEach(() => vi.unstubAllGlobals())

const dashKey = `GET /orgs/${ORG_ID}/dashboard`
const requestsPath = `/orgs/${ORG_ID}/requests`

const zero = { DRAFT: 0, SUBMITTED: 0, CHANGES_REQUESTED: 0, APPROVED: 0, REJECTED: 0 }

function dashboard(overrides: Record<string, unknown> = {}) {
  return {
    scope: "ORGANIZATION",
    total: 7,
    byStatus: { ...zero, DRAFT: 2, SUBMITTED: 3, APPROVED: 1, REJECTED: 1 },
    awaitingReview: 2,
    mine: { total: 1, byStatus: { ...zero, DRAFT: 1 } },
    recent: [],
    ...overrides,
  }
}

function recent(overrides: Record<string, unknown> = {}) {
  return {
    id: "e-1",
    requestId: "r-1",
    reference: "REQ-000005",
    title: "Standing desk",
    action: "APPROVE",
    toStatus: "APPROVED",
    actorName: "Dan",
    occurredAt: "2026-10-09T10:00:00Z",
    ...overrides,
  }
}

function render(role: OrgRole, extra: Record<string, () => Response> = {}) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [dashKey]: () => jsonResponse(200, dashboard()),
    ...extra,
  })
  renderWithProviders(
    <Routes>
      <Route path="/" element={<p>Home</p>} />
      <Route path="/orgs/:orgId" element={<OrgLayout />}>
        <Route index element={<DashboardPage />} />
      </Route>
    </Routes>,
    [`/orgs/${ORG_ID}`],
  )
  return fetchMock
}

describe("DashboardPage", () => {
  it("shows a reviewer the organization's counts, each linking to the filtered list", async () => {
    render("MANAGER")

    expect(await screen.findByRole("heading", { name: "Requests" })).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Submitted 3" })).toHaveAttribute(
      "href",
      `${requestsPath}?status=SUBMITTED`,
    )
    expect(screen.getByRole("link", { name: "Draft 2" })).toHaveAttribute(
      "href",
      `${requestsPath}?status=DRAFT`,
    )
    expect(screen.getByRole("link", { name: "Changes requested 0" })).toBeInTheDocument()
    expect(screen.getByText("7 in total.")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "You created 1 request." })).toHaveAttribute(
      "href",
      `${requestsPath}?mine=true`,
    )
  })

  it("shows what is waiting for the reviewer, linking to the matching list", async () => {
    render("MANAGER")

    expect(await screen.findByRole("heading", { name: "Awaiting your review" })).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "2" })).toHaveAttribute(
      "href",
      `${requestsPath}?reviewable=true`,
    )
    expect(screen.getByText(/requests submitted by others/)).toBeInTheDocument()
  })

  it("says so when nothing is waiting", async () => {
    render("MANAGER", { [dashKey]: () => jsonResponse(200, dashboard({ awaitingReview: 0 })) })

    expect(await screen.findByText("Nothing is waiting for your review.")).toBeInTheDocument()
  })

  it("shows an employee only their own requests and no review section", async () => {
    render("EMPLOYEE", {
      [dashKey]: () =>
        jsonResponse(
          200,
          dashboard({
            scope: "MINE",
            total: 3,
            byStatus: { ...zero, DRAFT: 1, SUBMITTED: 2 },
            awaitingReview: null,
            mine: { total: 3, byStatus: { ...zero, DRAFT: 1, SUBMITTED: 2 } },
          }),
        ),
    })

    expect(await screen.findByRole("heading", { name: "Your requests" })).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Submitted 2" })).toBeInTheDocument()
    expect(screen.queryByText("Awaiting your review")).not.toBeInTheDocument()
    expect(screen.queryByText(/in total/)).not.toBeInTheDocument()
    expect(screen.queryByText(/You created/)).not.toBeInTheDocument()
  })

  it("lists recent activity with links and renders text safely", async () => {
    render("MANAGER", {
      [dashKey]: () =>
        jsonResponse(
          200,
          dashboard({
            recent: [
              recent(),
              recent({
                id: "e-2",
                requestId: "r-2",
                reference: "REQ-000006",
                title: "<img src=x onerror=alert(1)>",
                action: "SUBMIT",
                toStatus: "SUBMITTED",
                actorName: "Carol",
              }),
            ],
          }),
        ),
    })

    expect(await screen.findByText("Dan")).toBeInTheDocument()
    expect(screen.getByText(/approved the request/)).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "REQ-000005 · Standing desk" })).toHaveAttribute(
      "href",
      `${requestsPath}/r-1`,
    )
    expect(
      screen.getByRole("link", { name: "REQ-000006 · <img src=x onerror=alert(1)>" }),
    ).toBeInTheDocument()
    expect(document.querySelector("img")).toBeNull()
  })

  it("explains an empty organization and offers to create the first request", async () => {
    render("EMPLOYEE", {
      [dashKey]: () =>
        jsonResponse(
          200,
          dashboard({
            scope: "MINE",
            total: 0,
            byStatus: zero,
            awaitingReview: null,
            mine: { total: 0, byStatus: zero },
          }),
        ),
    })

    expect(await screen.findByText(/No requests yet\./)).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Create a request" })).toHaveAttribute(
      "href",
      `${requestsPath}/new`,
    )
    expect(screen.getByText(/Activity appears when requests are submitted/)).toBeInTheDocument()
  })

  it("shows the role and what it allows", async () => {
    render("EMPLOYEE")

    expect(await screen.findByText("You are signed in here as Employee.")).toBeInTheDocument()
    expect(screen.getByText("What your role allows")).toBeInTheDocument()
  })

  it("shows an error with a retry", async () => {
    let calls = 0
    render("MANAGER", {
      [dashKey]: () =>
        ++calls === 1 ? jsonResponse(500, { status: 500 }) : jsonResponse(200, dashboard()),
    })

    expect(await screen.findByText("Couldn't load the dashboard.")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }))
    expect(await screen.findByRole("link", { name: "Submitted 3" })).toBeInTheDocument()
  })
})
