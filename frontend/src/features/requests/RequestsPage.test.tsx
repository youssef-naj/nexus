import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, orgDetail, pageOf, renderOrgRoutes, requestedUrls } from "@/test/orgFixtures"
import type { OrgRole } from "@/features/organizations/schemas"
import { RequestsPage } from "./RequestsPage"

afterEach(() => vi.unstubAllGlobals())

const listKey = `GET /orgs/${ORG_ID}/requests`

function summary(overrides: Record<string, unknown> = {}) {
  return {
    id: "r-1",
    reference: "REQ-000001",
    title: "Replace my laptop",
    category: "IT_SUPPORT",
    status: "DRAFT",
    createdByMembershipId: "m-1",
    createdByName: "Carol",
    departmentId: null,
    departmentName: null,
    dueDate: null,
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-02T10:00:00Z",
    version: 0,
    ...overrides,
  }
}

function renderPage(
  role: OrgRole,
  extra: Record<string, () => Response> = {},
  items = [summary()],
) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail(role)),
    [`GET /orgs/${ORG_ID}/departments`]: () =>
      jsonResponse(
        200,
        pageOf([
          {
            id: "d-1",
            name: "Engineering",
            description: null,
            active: true,
            version: 0,
            createdAt: "2026-10-01T10:00:00Z",
            updatedAt: "2026-10-01T10:00:00Z",
          },
        ]),
      ),
    [listKey]: () => jsonResponse(200, pageOf(items)),
    ...extra,
  })
  renderOrgRoutes(
    <>
      <Route path="requests" element={<RequestsPage />} />
      <Route path="requests/new" element={<p>New request page</p>} />
      <Route path="requests/:requestId" element={<p>Request detail page</p>} />
    </>,
    `/orgs/${ORG_ID}/requests`,
  )
  return fetchMock
}

const lastListUrl = (fetchMock: ReturnType<typeof mockApi>) =>
  requestedUrls(fetchMock, "/requests?").at(-1) ?? ""

describe("RequestsPage", () => {
  it("shows an employee their own requests without reviewer-only controls", async () => {
    renderPage("EMPLOYEE")

    expect(await screen.findByRole("link", { name: "Replace my laptop" })).toBeInTheDocument()
    expect(screen.getByText("You see the requests you created.")).toBeInTheDocument()
    expect(screen.queryByLabelText("Only my requests")).not.toBeInTheDocument()
    expect(screen.queryByRole("columnheader", { name: "Created by" })).not.toBeInTheDocument()
    expect(screen.getByRole("link", { name: "New request" })).toHaveAttribute(
      "href",
      `/orgs/${ORG_ID}/requests/new`,
    )
  })

  it("shows reviewers everyone's requests with the creator", async () => {
    renderPage("MANAGER")

    expect(await screen.findByRole("columnheader", { name: "Created by" })).toBeInTheDocument()
    expect(screen.getByText("Carol")).toBeInTheDocument()
    expect(screen.getByLabelText("Only my requests")).toBeInTheDocument()
  })

  it("sends filters and sorting as query parameters", async () => {
    const fetchMock = renderPage("MANAGER")
    const user = userEvent.setup()
    await screen.findByRole("link", { name: "Replace my laptop" })

    await user.selectOptions(screen.getByLabelText("Status"), "SUBMITTED")
    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("status=SUBMITTED"))
    await user.selectOptions(screen.getByLabelText("Category"), "FINANCE")
    await user.selectOptions(screen.getByLabelText("Department"), "d-1")
    await user.click(screen.getByLabelText("Only my requests"))
    await user.selectOptions(screen.getByLabelText("Sort by"), "dueSoon")

    await waitFor(() => {
      const url = lastListUrl(fetchMock)
      expect(url).toContain("status=SUBMITTED")
      expect(url).toContain("category=FINANCE")
      expect(url).toContain("departmentId=d-1")
      expect(url).toContain("mine=true")
      expect(url).toContain("sort=DUE_DATE")
      expect(url).toContain("direction=ASC")
    })
  })

  it("searches by text with the value encoded", async () => {
    const fetchMock = renderPage("EMPLOYEE")
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Search requests"), "100% & more")
    await user.click(screen.getByRole("button", { name: "Search" }))

    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("q=100%25+%26+more"))
  })

  it("moves between pages", async () => {
    const fetchMock = renderPage("EMPLOYEE", {
      [listKey]: () => jsonResponse(200, pageOf([summary()], { totalElements: 25, totalPages: 2 })),
    })
    const user = userEvent.setup()

    expect(await screen.findByText("Page 1 of 2")).toBeInTheDocument()
    await user.click(screen.getByRole("button", { name: "Next page" }))

    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("page=1"))
  })

  it("explains an empty list differently when filters are active", async () => {
    renderPage("EMPLOYEE", {}, [])
    expect(await screen.findByText("No requests yet.")).toBeInTheDocument()

    await userEvent.setup().selectOptions(screen.getByLabelText("Status"), "APPROVED")

    expect(await screen.findByText("No requests match these filters.")).toBeInTheDocument()
  })

  it("shows an error with a retry", async () => {
    let calls = 0
    renderPage("EMPLOYEE", {
      [listKey]: () =>
        ++calls === 1 ? jsonResponse(500, { status: 500 }) : jsonResponse(200, pageOf([summary()])),
    })

    expect(await screen.findByText("Couldn't load the requests.")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }))
    expect(await screen.findByRole("link", { name: "Replace my laptop" })).toBeInTheDocument()
  })
})
