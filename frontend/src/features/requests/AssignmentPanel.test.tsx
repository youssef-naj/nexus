import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import type { OrgRole } from "@/features/organizations/schemas"
import { ORG_ID, orgDetail, renderOrgRoutes } from "@/test/orgFixtures"
import { jsonResponse, mockApi } from "@/test/utils"
import { RequestDetailPage } from "./RequestDetailPage"

afterEach(() => vi.unstubAllGlobals())

const requestKey = `GET /orgs/${ORG_ID}/requests/r-1`
const eventsKey = `GET /orgs/${ORG_ID}/requests/r-1/events`
const reviewersKey = `GET /orgs/${ORG_ID}/reviewers`
const assigneeKey = `/orgs/${ORG_ID}/requests/r-1/assignee`

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: "r-1",
    reference: "REQ-000001",
    title: "Replace my laptop",
    description: null,
    category: "IT_SUPPORT",
    status: "SUBMITTED",
    createdByMembershipId: "m-carol",
    createdByName: "Carol",
    assigneeMembershipId: "m-ann",
    assigneeName: "Ann",
    departmentId: null,
    departmentName: null,
    dueDate: null,
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
    version: 3,
    editable: false,
    actions: [],
    assignable: true,
    ...overrides,
  }
}

const reviewers = [
  { membershipId: "m-dan", displayName: "Dan", role: "MANAGER", you: true },
  { membershipId: "m-ann", displayName: "Ann", role: "ADMIN", you: false },
  { membershipId: "m-eve", displayName: "Eve", role: "ADMIN", you: false },
  { membershipId: "m-carol", displayName: "Carol", role: "MANAGER", you: false },
]

function render(role: OrgRole = "MANAGER", extra: Record<string, () => Response> = {}) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [requestKey]: () => jsonResponse(200, detail()),
    [eventsKey]: () => jsonResponse(200, []),
    [reviewersKey]: () => jsonResponse(200, reviewers),
    ...extra,
  })
  renderOrgRoutes(
    <Route path="requests/:requestId" element={<RequestDetailPage />} />,
    `/orgs/${ORG_ID}/requests/r-1`,
  )
  return fetchMock
}

const callsWith = (fetchMock: ReturnType<typeof mockApi>, method: string) =>
  fetchMock.mock.calls.filter(([, init]) => init?.method === method)

describe("AssignmentPanel", () => {
  it("is not shown when the server says the viewer cannot assign", async () => {
    render("EMPLOYEE", { [requestKey]: () => jsonResponse(200, detail({ assignable: false })) })

    await screen.findByRole("heading", { name: "Replace my laptop" })
    expect(screen.queryByRole("heading", { name: "Assignment" })).not.toBeInTheDocument()
    expect(screen.queryByLabelText("Assign to")).not.toBeInTheDocument()
  })

  it("offers reviewers except the creator and the current assignee", async () => {
    render()

    const select = await screen.findByLabelText("Assign to")
    await screen.findByRole("option", { name: "Dan" })
    expect(
      within(select)
        .getAllByRole("option")
        .map((option) => option.textContent),
    ).toEqual(["Choose a reviewer…", "Dan", "Eve"])
    expect(screen.getByText(/Assigned to Ann/)).toBeInTheDocument()
  })

  it("says so when nobody is assigned and offers no removal", async () => {
    render("MANAGER", {
      [requestKey]: () =>
        jsonResponse(200, detail({ assigneeMembershipId: null, assigneeName: null })),
    })

    expect(await screen.findByText(/Not assigned yet/)).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Remove assignee" })).not.toBeInTheDocument()
  })

  it("assigns the chosen reviewer with the version the page saw", async () => {
    const fetchMock = render("MANAGER", {
      [`PUT ${assigneeKey}`]: () =>
        jsonResponse(
          200,
          detail({ assigneeMembershipId: "m-eve", assigneeName: "Eve", version: 4 }),
        ),
    })
    const user = userEvent.setup()

    await screen.findByRole("option", { name: "Eve" })
    await user.selectOptions(screen.getByLabelText("Assign to"), "m-eve")
    await user.click(screen.getByRole("button", { name: "Assign" }))

    expect(await screen.findByText("Assigned to Eve.")).toBeInTheDocument()
    const put = callsWith(fetchMock, "PUT")[0]?.[1]
    expect(JSON.parse(String(put?.body))).toEqual({ membershipId: "m-eve", version: 3 })
    expect(new Headers(put?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("assigns to the viewer with one click", async () => {
    const fetchMock = render("MANAGER", {
      [`PUT ${assigneeKey}`]: () =>
        jsonResponse(
          200,
          detail({ assigneeMembershipId: "m-dan", assigneeName: "Dan", version: 4 }),
        ),
    })

    await userEvent.setup().click(await screen.findByRole("button", { name: "Assign to me" }))

    expect(await screen.findByText("Assigned to you.")).toBeInTheDocument()
    expect(JSON.parse(String(callsWith(fetchMock, "PUT")[0]?.[1]?.body))).toEqual({
      membershipId: "m-dan",
      version: 3,
    })
  })

  it("removes the assignee with a DELETE that carries the version", async () => {
    const fetchMock = render("MANAGER", {
      [`DELETE ${assigneeKey}`]: () =>
        jsonResponse(200, detail({ assigneeMembershipId: null, assigneeName: null, version: 4 })),
    })

    await userEvent.setup().click(await screen.findByRole("button", { name: "Remove assignee" }))

    expect(await screen.findByText("Assignee removed.")).toBeInTheDocument()
    const call = callsWith(fetchMock, "DELETE")[0]
    expect(String(call?.[0])).toContain("version=3")
    expect(call?.[1]?.body).toBeUndefined()
  })

  it("explains a conflict and reloads", async () => {
    render("MANAGER", {
      [`PUT ${assigneeKey}`]: () => jsonResponse(409, { status: 409, code: "STALE_VERSION" }),
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Assign to me" }))

    expect(await screen.findByText(/Someone else changed this request/)).toBeInTheDocument()
  })

  it("shows a friendly message when the server rejects the chosen reviewer", async () => {
    render("MANAGER", {
      [`PUT ${assigneeKey}`]: () =>
        jsonResponse(400, {
          status: 400,
          errors: { membershipId: ["must be a member who can review this request"] },
        }),
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Assign to me" }))

    expect(
      await screen.findByText("Choose a reviewer who can review this request."),
    ).toBeInTheDocument()
  })

  it("shows assignments in the history with who was assigned", async () => {
    render("MANAGER", {
      [eventsKey]: () =>
        jsonResponse(200, [
          {
            id: "e-1",
            action: "ASSIGN",
            fromStatus: "SUBMITTED",
            toStatus: "SUBMITTED",
            comment: null,
            actorMembershipId: "m-dan",
            actorName: "Dan",
            targetName: "Ann",
            occurredAt: "2026-10-02T10:00:00Z",
          },
          {
            id: "e-2",
            action: "UNASSIGN",
            fromStatus: "SUBMITTED",
            toStatus: "SUBMITTED",
            comment: null,
            actorMembershipId: "m-dan",
            actorName: "Dan",
            targetName: "Ann",
            occurredAt: "2026-10-03T10:00:00Z",
          },
        ]),
    })

    expect(await screen.findByText("Dan assigned the request to Ann")).toBeInTheDocument()
    expect(screen.getByText("Dan removed Ann as assignee")).toBeInTheDocument()
  })
})
