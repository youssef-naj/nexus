import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, orgDetail, renderOrgRoutes } from "@/test/orgFixtures"
import type { OrgRole } from "@/features/organizations/schemas"
import { RequestDetailPage } from "./RequestDetailPage"

afterEach(() => vi.unstubAllGlobals())

const requestKey = `GET /orgs/${ORG_ID}/requests/r-1`
const eventsKey = `GET /orgs/${ORG_ID}/requests/r-1/events`
const transitionKey = `POST /orgs/${ORG_ID}/requests/r-1/transitions`

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: "r-1",
    reference: "REQ-000001",
    title: "Replace my laptop",
    description: "Line one\nLine two",
    category: "IT_SUPPORT",
    status: "DRAFT",
    createdByMembershipId: "m-1",
    createdByName: "Carol",
    assigneeMembershipId: null,
    assigneeName: null,
    departmentId: "d-1",
    departmentName: "Engineering",
    dueDate: null,
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
    version: 3,
    editable: true,
    actions: [],
    ...overrides,
  }
}

function event(overrides: Record<string, unknown> = {}) {
  return {
    id: "e-1",
    action: "SUBMIT",
    fromStatus: "DRAFT",
    toStatus: "SUBMITTED",
    comment: null,
    actorMembershipId: "m-1",
    actorName: "Carol",
    occurredAt: "2026-10-02T10:00:00Z",
    ...overrides,
  }
}

function render(role: OrgRole = "EMPLOYEE", extra: Record<string, () => Response> = {}) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [requestKey]: () => jsonResponse(200, detail()),
    [eventsKey]: () => jsonResponse(200, []),
    ...extra,
  })
  renderOrgRoutes(
    <Route path="requests/:requestId" element={<RequestDetailPage />} />,
    `/orgs/${ORG_ID}/requests/r-1`,
  )
  return fetchMock
}

const posts = (fetchMock: ReturnType<typeof mockApi>) =>
  fetchMock.mock.calls.filter(([, init]) => init?.method === "POST")

describe("RequestDetailPage", () => {
  it("shows the request, an edit link when the server says it is editable, and an empty history", async () => {
    render()

    expect(await screen.findByRole("heading", { name: "Replace my laptop" })).toBeInTheDocument()
    expect(screen.getByText("REQ-000001")).toBeInTheDocument()
    expect(screen.getByText("Draft")).toBeInTheDocument()
    expect(screen.getByText("Engineering")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Edit request" })).toHaveAttribute(
      "href",
      "/orgs/" + ORG_ID + "/requests/r-1/edit",
    )
    expect(await screen.findByText(/No history yet/)).toBeInTheDocument()
  })

  it("shows no edit link when the request is not editable by this viewer", async () => {
    render("EMPLOYEE", {
      [requestKey]: () => jsonResponse(200, detail({ editable: false, status: "SUBMITTED" })),
    })

    expect(await screen.findByText("Submitted")).toBeInTheDocument()
    expect(screen.queryByRole("link", { name: "Edit request" })).not.toBeInTheDocument()
  })

  it("renders text safely, as text and with its line breaks", async () => {
    render("EMPLOYEE", {
      [requestKey]: () =>
        jsonResponse(
          200,
          detail({
            title: "<img src=x onerror=alert(1)>",
            description: "<b>bold?</b>\nsecond line",
          }),
        ),
      [eventsKey]: () => jsonResponse(200, [event({ comment: "<script>alert(1)</script>" })]),
    })

    expect(
      await screen.findByRole("heading", { name: "<img src=x onerror=alert(1)>" }),
    ).toBeInTheDocument()
    expect(screen.getByText(/<b>bold\?<\/b>/)).toBeInTheDocument()
    expect(await screen.findByText("<script>alert(1)</script>")).toBeInTheDocument()
    expect(document.querySelector("img")).toBeNull()
    expect(document.querySelector("b")).toBeNull()
    expect(document.querySelector("script")).toBeNull()
  })

  it("shows one neutral screen for a request that is missing or not yours to see", async () => {
    render("EMPLOYEE", { [requestKey]: () => jsonResponse(404, { status: 404 }) })

    expect(await screen.findByText("Request not found")).toBeInTheDocument()
  })

  it("lists the history in order with who did what and the comments", async () => {
    render("EMPLOYEE", {
      [eventsKey]: () =>
        jsonResponse(200, [
          event({ comment: "Needed by Friday" }),
          event({
            id: "e-2",
            action: "REQUEST_CHANGES",
            fromStatus: "SUBMITTED",
            toStatus: "CHANGES_REQUESTED",
            actorName: "Dan",
            comment: "Add a budget",
          }),
        ]),
    })

    expect(await screen.findByText("Carol submitted the request")).toBeInTheDocument()
    expect(screen.getByText("Dan requested changes")).toBeInTheDocument()
    expect(screen.getByText("Needed by Friday")).toBeInTheDocument()
    expect(screen.getByText("Add a budget")).toBeInTheDocument()
    expect(screen.getByText(/Draft → Submitted/)).toBeInTheDocument()
  })

  it("lets the creator submit with an optional comment and shows the outcome", async () => {
    let submitted = false
    const fetchMock = render("EMPLOYEE", {
      [requestKey]: () =>
        jsonResponse(
          200,
          submitted
            ? detail({ status: "SUBMITTED", version: 4, editable: false, actions: [] })
            : detail({ actions: ["SUBMIT"] }),
        ),
      [transitionKey]: () => {
        submitted = true
        return jsonResponse(
          200,
          detail({ status: "SUBMITTED", version: 4, editable: false, actions: [] }),
        )
      },
    })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Comment (optional)"), "  Please hurry  ")
    await user.click(screen.getByRole("button", { name: "Submit for review" }))

    expect(await screen.findByText("Request submitted for review.")).toBeInTheDocument()
    expect(await screen.findByText("Submitted")).toBeInTheDocument()
    const body = JSON.parse(String(posts(fetchMock)[0]?.[1]?.body))
    expect(body).toEqual({ action: "SUBMIT", version: 3, comment: "Please hurry" })
    expect(new Headers(posts(fetchMock)[0]?.[1]?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
    await waitFor(() =>
      expect(screen.queryByRole("button", { name: "Submit for review" })).not.toBeInTheDocument(),
    )
  })

  it("offers a reviewer exactly the actions the server listed", async () => {
    render("MANAGER", {
      [requestKey]: () =>
        jsonResponse(
          200,
          detail({
            status: "SUBMITTED",
            editable: false,
            actions: ["APPROVE", "REJECT", "TELEPORT"],
          }),
        ),
    })

    expect(await screen.findByRole("button", { name: "Approve" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Request changes" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /Teleport/i })).not.toBeInTheDocument()
  })

  it("shows no workflow panel when the viewer has no actions", async () => {
    render("MANAGER", {
      [requestKey]: () =>
        jsonResponse(200, detail({ status: "SUBMITTED", editable: false, actions: [] })),
    })

    await screen.findByText("Submitted")
    expect(screen.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument()
    expect(screen.queryByText("Review")).not.toBeInTheDocument()
  })

  it("requires a comment to reject and sends nothing without one", async () => {
    const fetchMock = render("MANAGER", {
      [requestKey]: () =>
        jsonResponse(
          200,
          detail({
            status: "SUBMITTED",
            editable: false,
            actions: ["APPROVE", "REJECT", "REQUEST_CHANGES"],
          }),
        ),
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Reject" }))
    expect(await screen.findByText("Enter a comment explaining why.")).toBeInTheDocument()
    await user.click(screen.getByRole("button", { name: "Request changes" }))

    expect(posts(fetchMock)).toHaveLength(0)
  })

  it("approves without a comment", async () => {
    const fetchMock = render("MANAGER", {
      [requestKey]: () =>
        jsonResponse(200, detail({ status: "SUBMITTED", editable: false, actions: ["APPROVE"] })),
      [transitionKey]: () =>
        jsonResponse(200, detail({ status: "APPROVED", version: 4, editable: false, actions: [] })),
    })

    await userEvent.setup().click(await screen.findByRole("button", { name: "Approve" }))

    expect(await screen.findByText("Request approved.")).toBeInTheDocument()
    expect(JSON.parse(String(posts(fetchMock)[0]?.[1]?.body))).toEqual({
      action: "APPROVE",
      version: 3,
    })
  })

  it("rejects with a trimmed comment", async () => {
    const fetchMock = render("MANAGER", {
      [requestKey]: () =>
        jsonResponse(
          200,
          detail({ status: "SUBMITTED", editable: false, actions: ["APPROVE", "REJECT"] }),
        ),
      [transitionKey]: () =>
        jsonResponse(200, detail({ status: "REJECTED", version: 4, editable: false, actions: [] })),
    })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Comment"), "  Out of budget ")
    await user.click(screen.getByRole("button", { name: "Reject" }))

    expect(await screen.findByText("Request rejected.")).toBeInTheDocument()
    expect(JSON.parse(String(posts(fetchMock)[0]?.[1]?.body))).toEqual({
      action: "REJECT",
      version: 3,
      comment: "Out of budget",
    })
  })

  it("explains a stale version and reloads the request", async () => {
    let loads = 0
    render("MANAGER", {
      [requestKey]: () => {
        loads += 1
        return jsonResponse(
          200,
          detail({ status: "SUBMITTED", editable: false, actions: ["APPROVE"] }),
        )
      },
      [transitionKey]: () => jsonResponse(409, { status: 409, code: "STALE_VERSION" }),
    })

    await userEvent.setup().click(await screen.findByRole("button", { name: "Approve" }))

    expect(await screen.findByText(/Someone else changed this request/)).toBeInTheDocument()
    await waitFor(() => expect(loads).toBeGreaterThanOrEqual(2))
  })

  it("explains when the request was already handled", async () => {
    render("MANAGER", {
      [requestKey]: () =>
        jsonResponse(200, detail({ status: "SUBMITTED", editable: false, actions: ["APPROVE"] })),
      [transitionKey]: () => jsonResponse(409, { status: 409, code: "INVALID_TRANSITION" }),
    })

    await userEvent.setup().click(await screen.findByRole("button", { name: "Approve" }))

    expect(await screen.findByText(/already handled/)).toBeInTheDocument()
  })

  it("shows the server's comment error under the field", async () => {
    render("MANAGER", {
      [requestKey]: () =>
        jsonResponse(200, detail({ status: "SUBMITTED", editable: false, actions: ["APPROVE"] })),
      [transitionKey]: () =>
        jsonResponse(400, {
          status: 400,
          errors: { comment: ["must be at most 1000 characters"] },
        }),
    })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Comment (optional)"), "x")
    await user.click(screen.getByRole("button", { name: "Approve" }))

    expect(await screen.findByText("Comment must be at most 1000 characters")).toBeInTheDocument()
  })
})
