import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, memberJson, orgDetail, pageOf, renderOrgRoutes } from "@/test/orgFixtures"
import type { OrgRole } from "@/features/organizations/schemas"
import { DepartmentDetailPage } from "./DepartmentDetailPage"

afterEach(() => vi.unstubAllGlobals())

const dept = (overrides: Record<string, unknown> = {}) => ({
  id: "d-1",
  name: "Engineering",
  description: "Builds things",
  active: true,
  version: 2,
  createdAt: "2026-10-01T10:00:00Z",
  updatedAt: "2026-10-01T10:00:00Z",
  ...overrides,
})
const assigned = {
  membershipId: "m-carol",
  userId: "u-carol",
  displayName: "Carol",
  email: null,
  role: "EMPLOYEE",
  assignedAt: "2026-10-02T10:00:00Z",
}
const deptKey = `GET /orgs/${ORG_ID}/departments/d-1`

function renderPage(role: OrgRole, extra: Record<string, () => Response> = {}) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [deptKey]: () => jsonResponse(200, dept()),
    [`${deptKey}/members`]: () => jsonResponse(200, pageOf([assigned])),
    [`GET /orgs/${ORG_ID}/members`]: () =>
      jsonResponse(
        200,
        pageOf([
          memberJson({ id: "m-carol", displayName: "Carol" }),
          memberJson({ id: "m-dan", displayName: "Dan", role: "MANAGER" }),
        ]),
      ),
    ...extra,
  })
  renderOrgRoutes(
    <Route path="departments/:departmentId" element={<DepartmentDetailPage />} />,
    `/orgs/${ORG_ID}/departments/d-1`,
  )
  return fetchMock
}

describe("DepartmentDetailPage", () => {
  it("shows a read-only view to members who cannot manage departments", async () => {
    renderPage("EMPLOYEE")

    expect(await screen.findByRole("heading", { name: "Engineering" })).toBeInTheDocument()
    expect(screen.getByText("Builds things")).toBeInTheDocument()
    expect(await screen.findByText("Carol")).toBeInTheDocument()
    expect(screen.queryByText("Edit department")).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /Remove Carol/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText("Add a member")).not.toBeInTheDocument()
  })

  it("lets an admin add a member from the organization", async () => {
    const fetchMock = renderPage("ADMIN", {
      [`PUT /orgs/${ORG_ID}/departments/d-1/members/m-dan`]: () => jsonResponse(204),
    })
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText("Add a member"), "m-dan")
    await user.click(screen.getByRole("button", { name: "Add member" }))

    expect(await screen.findByText("Added Dan.")).toBeInTheDocument()
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT")
    expect(new Headers(put?.[1]?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("removes a member from the department", async () => {
    const fetchMock = renderPage("ADMIN", {
      [`DELETE /orgs/${ORG_ID}/departments/d-1/members/m-carol`]: () => jsonResponse(204),
    })

    await userEvent
      .setup()
      .click(await screen.findByRole("button", { name: "Remove Carol from department" }))

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === "DELETE")).toBe(true),
    )
  })

  it("saves edits with the version it loaded and explains a conflict", async () => {
    const fetchMock = renderPage("ADMIN", {
      [`PUT /orgs/${ORG_ID}/departments/d-1`]: () =>
        jsonResponse(409, { status: 409, code: "STALE_VERSION" }),
    })
    const user = userEvent.setup()

    const name = await screen.findByLabelText("Name")
    await waitFor(() => expect(name).toHaveValue("Engineering"))
    await user.clear(name)
    await user.type(name, "Platform")
    await user.click(screen.getByRole("button", { name: "Save changes" }))

    expect(await screen.findByText(/Someone else changed this department/)).toBeInTheDocument()
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT")?.[1]
    expect(JSON.parse(String(put?.body))).toEqual({
      name: "Platform",
      description: "Builds things",
      version: 2,
    })
  })

  it("asks for confirmation before deactivating", async () => {
    const fetchMock = renderPage("ADMIN", {
      [`POST /orgs/${ORG_ID}/departments/d-1/deactivate`]: () =>
        jsonResponse(200, dept({ active: false })),
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Deactivate department" }))
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false)
    await user.click(screen.getByRole("button", { name: "Confirm deactivation" }))

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(true),
    )
  })

  it("does not offer new members to an inactive department", async () => {
    renderPage("ADMIN", { [deptKey]: () => jsonResponse(200, dept({ active: false })) })

    expect(
      await screen.findByText("Inactive departments cannot receive new members."),
    ).toBeInTheDocument()
    expect(screen.queryByLabelText("Add a member")).not.toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Reactivate department" })).toBeInTheDocument()
  })

  it("shows one neutral screen for a department that is missing or not in this organization", async () => {
    renderPage("EMPLOYEE", { [deptKey]: () => jsonResponse(404, { status: 404 }) })

    expect(await screen.findByText("Department not found")).toBeInTheDocument()
  })
})
