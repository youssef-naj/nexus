import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, orgDetail, pageOf, renderOrgRoutes, requestedUrls } from "@/test/orgFixtures"
import type { OrgRole } from "@/features/organizations/schemas"
import { DepartmentsPage } from "./DepartmentsPage"

afterEach(() => vi.unstubAllGlobals())

const listKey = `GET /orgs/${ORG_ID}/departments`

function department(overrides: Record<string, unknown> = {}) {
  return {
    id: "d-1",
    name: "Engineering",
    description: "Builds things",
    active: true,
    version: 0,
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
    ...overrides,
  }
}

function renderPage(
  role: OrgRole,
  extra: Record<string, () => Response> = {},
  items = [department()],
) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [listKey]: () => jsonResponse(200, pageOf(items)),
    ...extra,
  })
  renderOrgRoutes(
    <>
      <Route path="departments" element={<DepartmentsPage />} />
      <Route path="departments/:departmentId" element={<p>Department detail</p>} />
    </>,
    `/orgs/${ORG_ID}/departments`,
  )
  return fetchMock
}

describe("DepartmentsPage", () => {
  it("lists active departments by default and flags inactive ones when shown", async () => {
    const fetchMock = renderPage("EMPLOYEE", {}, [
      department(),
      department({ id: "d-2", name: "Archive", active: false }),
    ])

    expect(await screen.findByRole("link", { name: "Engineering" })).toHaveAttribute(
      "href",
      "/orgs/" + ORG_ID + "/departments/d-1",
    )
    // "Inactive" is also an option in the state filter, so scope the query to the badge
    expect(screen.getByText("Inactive", { selector: '[data-slot="badge"]' })).toBeInTheDocument()
    expect(requestedUrls(fetchMock, "/departments?")[0]).toContain("active=true")
  })

  it("hides the creation form from members who cannot manage departments", async () => {
    renderPage("EMPLOYEE")

    await screen.findByRole("link", { name: "Engineering" })
    expect(screen.queryByText("New department")).not.toBeInTheDocument()
  })

  it("lets an admin create a department and refreshes the list", async () => {
    let loads = 0
    const fetchMock = renderPage(
      "ADMIN",
      {
        [listKey]: () => {
          loads += 1
          return jsonResponse(200, pageOf(loads === 1 ? [] : [department({ name: "Finance" })]))
        },
        [`POST /orgs/${ORG_ID}/departments`]: () =>
          jsonResponse(201, department({ name: "Finance" })),
      },
      [],
    )
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Name"), "  Finance ")
    await user.type(screen.getByLabelText("Description"), "Money")
    await user.click(screen.getByRole("button", { name: "Create department" }))

    expect(await screen.findByText("Department created.")).toBeInTheDocument()
    expect(await screen.findByRole("link", { name: "Finance" })).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST")?.[1]
    expect(JSON.parse(String(post?.body))).toEqual({ name: "Finance", description: "Money" })
    expect(new Headers(post?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("validates the name in the browser", async () => {
    const fetchMock = renderPage("ADMIN")

    await userEvent.setup().click(await screen.findByRole("button", { name: "Create department" }))

    expect(await screen.findByText("Enter a name for the department.")).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false)
  })

  it("shows a taken name under the field", async () => {
    renderPage("ADMIN", {
      [`POST /orgs/${ORG_ID}/departments`]: () =>
        jsonResponse(409, { status: 409, code: "DEPARTMENT_NAME_TAKEN" }),
    })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Name"), "Engineering")
    await user.click(screen.getByRole("button", { name: "Create department" }))

    expect(
      await screen.findByText("A department with this name already exists."),
    ).toBeInTheDocument()
  })

  it("filters by state and searches", async () => {
    const fetchMock = renderPage("EMPLOYEE")
    const user = userEvent.setup()
    await screen.findByRole("link", { name: "Engineering" })

    await user.selectOptions(screen.getByLabelText("Show"), "all")
    await waitFor(() => {
      const last = requestedUrls(fetchMock, "/departments?").at(-1) ?? ""
      expect(last).not.toContain("active=")
    })

    await user.type(screen.getByLabelText("Search departments"), "eng")
    await user.click(screen.getByRole("button", { name: "Search" }))
    await waitFor(() => expect(requestedUrls(fetchMock, "/departments?").at(-1)).toContain("q=eng"))
  })

  it("shows an empty state and an error with retry", async () => {
    renderPage("EMPLOYEE", {}, [])
    expect(await screen.findByText("No departments found.")).toBeInTheDocument()
  })

  it("recovers from a failed load", async () => {
    let calls = 0
    renderPage("EMPLOYEE", {
      [listKey]: () =>
        ++calls === 1
          ? jsonResponse(500, { status: 500 })
          : jsonResponse(200, pageOf([department()])),
    })

    expect(await screen.findByText("Couldn't load the departments.")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }))
    expect(await screen.findByRole("link", { name: "Engineering" })).toBeInTheDocument()
  })
})
