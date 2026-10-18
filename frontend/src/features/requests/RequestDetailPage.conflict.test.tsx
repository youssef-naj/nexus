import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { ORG_ID, orgDetail, renderOrgRoutes } from "@/test/orgFixtures"
import { jsonResponse, mockApi } from "@/test/utils"
import { RequestDetailPage } from "./RequestDetailPage"

afterEach(() => vi.unstubAllGlobals())

const base = `/orgs/${ORG_ID}/requests/r-1`

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: "r-1",
    reference: "REQ-000001",
    title: "Contested request",
    description: null,
    category: "OTHER",
    status: "SUBMITTED",
    createdByMembershipId: "m-carol",
    createdByName: "Carol",
    assigneeMembershipId: null,
    assigneeName: null,
    departmentId: null,
    departmentName: null,
    dueDate: null,
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
    version: 1,
    editable: false,
    actions: ["APPROVE", "REJECT"],
    assignable: true,
    ...overrides,
  }
}

const reviewers = [{ membershipId: "m-dan", displayName: "Dan", role: "MANAGER", you: true }]

/** After the failed action the server shows the request as already decided: no actions at all. */
const decided = () => detail({ status: "APPROVED", version: 5, actions: [], assignable: false })

function render(extra: Record<string, () => Response>, handled: () => boolean) {
  mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail("MANAGER")),
    [`GET ${base}`]: () => jsonResponse(200, handled() ? decided() : detail()),
    [`GET ${base}/events`]: () => jsonResponse(200, []),
    [`GET /orgs/${ORG_ID}/reviewers`]: () => jsonResponse(200, reviewers),
    ...extra,
  })
  renderOrgRoutes(<Route path="requests/:requestId" element={<RequestDetailPage />} />, base)
}

describe("a conflict that takes away what the viewer could do", () => {
  it("still explains a review conflict after the review controls are gone", async () => {
    let handled = false
    render(
      {
        [`POST ${base}/transitions`]: () => {
          handled = true
          return jsonResponse(409, { status: 409, code: "INVALID_TRANSITION" })
        },
      },
      () => handled,
    )

    await userEvent.setup().click(await screen.findByRole("button", { name: "Approve" }))

    expect(await screen.findByText("Approved", { exact: true })).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument()
    expect(screen.getByText(/already handled/)).toBeInTheDocument()
  })

  it("still explains an assignment conflict after the assignment panel is gone", async () => {
    let handled = false
    render(
      {
        [`PUT ${base}/assignee`]: () => {
          handled = true
          return jsonResponse(409, { status: 409, code: "REQUEST_NOT_ASSIGNABLE" })
        },
      },
      () => handled,
    )

    await userEvent.setup().click(await screen.findByRole("button", { name: "Assign to me" }))

    expect(await screen.findByText("Approved", { exact: true })).toBeInTheDocument()
    expect(screen.queryByRole("heading", { name: "Assignment" })).not.toBeInTheDocument()
    expect(screen.getByText(/Only a submitted request can be assigned/)).toBeInTheDocument()
  })
})
