import { screen } from "@testing-library/react"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, orgDetail, renderOrgRoutes } from "@/test/orgFixtures"
import { RequestDetailPage } from "./RequestDetailPage"

afterEach(() => vi.unstubAllGlobals())

const requestKey = `GET /orgs/${ORG_ID}/requests/r-1`

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
    version: 0,
    editable: true,
    ...overrides,
  }
}

function render(extra: Record<string, () => Response>) {
  mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail("EMPLOYEE")),
    ...extra,
  })
  renderOrgRoutes(
    <Route path="requests/:requestId" element={<RequestDetailPage />} />,
    `/orgs/${ORG_ID}/requests/r-1`,
  )
}

describe("RequestDetailPage", () => {
  it("shows the request and an edit link when the server says it is editable", async () => {
    render({ [requestKey]: () => jsonResponse(200, detail()) })

    expect(await screen.findByRole("heading", { name: "Replace my laptop" })).toBeInTheDocument()
    expect(screen.getByText("REQ-000001")).toBeInTheDocument()
    expect(screen.getByText("Draft")).toBeInTheDocument()
    expect(screen.getByText("IT support")).toBeInTheDocument()
    expect(screen.getByText("Engineering")).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Edit request" })).toHaveAttribute(
      "href",
      "/orgs/" + ORG_ID + "/requests/r-1/edit",
    )
  })

  it("shows no edit link when the request is not editable by this viewer", async () => {
    render({
      [requestKey]: () => jsonResponse(200, detail({ editable: false, status: "SUBMITTED" })),
    })

    expect(await screen.findByText("Submitted")).toBeInTheDocument()
    expect(screen.queryByRole("link", { name: "Edit request" })).not.toBeInTheDocument()
  })

  it("renders text safely, as text and with its line breaks", async () => {
    render({
      [requestKey]: () =>
        jsonResponse(
          200,
          detail({
            title: "<img src=x onerror=alert(1)>",
            description: "<b>bold?</b>\nsecond line",
          }),
        ),
    })

    expect(
      await screen.findByRole("heading", { name: "<img src=x onerror=alert(1)>" }),
    ).toBeInTheDocument()
    expect(screen.getByText(/<b>bold\?<\/b>/)).toBeInTheDocument()
    expect(document.querySelector("img")).toBeNull()
    expect(document.querySelector("b")).toBeNull()
  })

  it("shows one neutral screen for a request that is missing or not yours to see", async () => {
    render({ [requestKey]: () => jsonResponse(404, { status: 404 }) })

    expect(await screen.findByText("Request not found")).toBeInTheDocument()
  })
})
