import { screen, waitFor } from "@testing-library/react"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import type { OrgRole } from "@/features/organizations/schemas"
import { ORG_ID, orgDetail, pageOf, renderOrgRoutes, requestedUrls } from "@/test/orgFixtures"
import { jsonResponse, mockApi } from "@/test/utils"
import { RequestsPage } from "./RequestsPage"

afterEach(() => vi.unstubAllGlobals())

function render(role: OrgRole, search: string) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [`GET /orgs/${ORG_ID}/departments`]: () => jsonResponse(200, pageOf([])),
    [`GET /orgs/${ORG_ID}/requests`]: () => jsonResponse(200, pageOf([])),
  })
  renderOrgRoutes(
    <Route path="requests" element={<RequestsPage />} />,
    `/orgs/${ORG_ID}/requests${search}`,
  )
  return fetchMock
}

const lastListUrl = (fetchMock: ReturnType<typeof mockApi>) =>
  requestedUrls(fetchMock, "/requests?").at(-1) ?? ""

describe("RequestsPage 'assigned to me'", () => {
  it("starts from the dashboard link for a reviewer and locks the status", async () => {
    const fetchMock = render("MANAGER", "?assignedToMe=true")

    expect(await screen.findByLabelText("Assigned to me")).toBeChecked()
    expect(screen.getByLabelText("Status")).toBeDisabled()
    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("assignedToMe=true"))
  })

  it("is not offered to employees and the link is ignored for them", async () => {
    const fetchMock = render("EMPLOYEE", "?assignedToMe=true")

    await screen.findByLabelText("Status")
    expect(screen.queryByLabelText("Assigned to me")).not.toBeInTheDocument()
    await waitFor(() => expect(lastListUrl(fetchMock)).not.toContain("assignedToMe"))
  })

  it("wraps the request table in a labelled scroll region once there are rows", async () => {
    mockApi({
      [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail("MANAGER")),
      [`GET /orgs/${ORG_ID}/departments`]: () => jsonResponse(200, pageOf([])),
      [`GET /orgs/${ORG_ID}/requests`]: () =>
        jsonResponse(
          200,
          pageOf([
            {
              id: "r-1",
              reference: "REQ-000001",
              title: "Desk",
              category: "HR",
              status: "SUBMITTED",
              createdByMembershipId: "m-1",
              createdByName: "Carol",
              departmentId: null,
              departmentName: null,
              dueDate: null,
              createdAt: "2026-10-01T10:00:00Z",
              updatedAt: "2026-10-02T10:00:00Z",
              version: 1,
            },
          ]),
        ),
    })
    renderOrgRoutes(
      <Route path="requests" element={<RequestsPage />} />,
      `/orgs/${ORG_ID}/requests`,
    )

    expect(await screen.findByRole("region", { name: "Requests" })).toBeInTheDocument()
  })
})
