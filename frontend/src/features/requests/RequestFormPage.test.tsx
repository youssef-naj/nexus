import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { jsonResponse, mockApi } from "@/test/utils"
import { ORG_ID, orgDetail, pageOf, renderOrgRoutes } from "@/test/orgFixtures"
import { RequestFormPage } from "./RequestFormPage"

afterEach(() => vi.unstubAllGlobals())

const departmentsKey = `GET /orgs/${ORG_ID}/departments`
const engineering = {
  id: "d-1",
  name: "Engineering",
  description: null,
  active: true,
  version: 0,
  createdAt: "2026-10-01T10:00:00Z",
  updatedAt: "2026-10-01T10:00:00Z",
}

function detail(overrides: Record<string, unknown> = {}) {
  return {
    id: "r-1",
    reference: "REQ-000001",
    title: "Replace my laptop",
    description: "Screen is cracked",
    category: "IT_SUPPORT",
    status: "DRAFT",
    createdByMembershipId: "m-1",
    createdByName: "Carol",
    assigneeMembershipId: null,
    assigneeName: null,
    departmentId: null,
    departmentName: null,
    dueDate: null,
    createdAt: "2026-10-01T10:00:00Z",
    updatedAt: "2026-10-01T10:00:00Z",
    version: 3,
    editable: true,
    ...overrides,
  }
}

function render(path: string, extra: Record<string, () => Response> = {}) {
  const fetchMock = mockApi({
    [`GET /orgs/${ORG_ID}`]: () => jsonResponse(200, orgDetail("EMPLOYEE")),
    [departmentsKey]: () => jsonResponse(200, pageOf([engineering])),
    ...extra,
  })
  renderOrgRoutes(
    <>
      <Route path="requests/new" element={<RequestFormPage />} />
      <Route path="requests/:requestId/edit" element={<RequestFormPage />} />
      <Route path="requests/:requestId" element={<p>Request detail page</p>} />
      <Route path="requests" element={<p>Request list page</p>} />
    </>,
    `/orgs/${ORG_ID}${path}`,
  )
  return fetchMock
}

describe("RequestFormPage (new)", () => {
  it("validates in the browser and sends nothing", async () => {
    const fetchMock = render("/requests/new")

    await userEvent.setup().click(await screen.findByRole("button", { name: "Create request" }))

    expect(await screen.findByText("Enter a title.")).toBeInTheDocument()
    expect(screen.getByText("Choose a category.")).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === "POST")).toBe(false)
  })

  it("creates a request with empty optional fields as null and opens it", async () => {
    const fetchMock = render("/requests/new", {
      [`POST /orgs/${ORG_ID}/requests`]: () => jsonResponse(201, detail({ version: 0 })),
    })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Title"), "  Replace my laptop ")
    await user.selectOptions(screen.getByLabelText("Category"), "IT_SUPPORT")
    await user.click(screen.getByRole("button", { name: "Create request" }))

    expect(await screen.findByText("Request detail page")).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST")?.[1]
    expect(JSON.parse(String(post?.body))).toEqual({
      title: "Replace my laptop",
      description: "",
      category: "IT_SUPPORT",
      dueDate: null,
      departmentId: null,
    })
    expect(new Headers(post?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("sends the chosen department and due date", async () => {
    const fetchMock = render("/requests/new", {
      [`POST /orgs/${ORG_ID}/requests`]: () => jsonResponse(201, detail()),
    })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Title"), "Laptop")
    await user.selectOptions(screen.getByLabelText("Category"), "HR")
    await user.selectOptions(await screen.findByRole("combobox", { name: "Department" }), "d-1")
    await user.type(screen.getByLabelText("Due date"), "2030-05-17")
    await user.click(screen.getByRole("button", { name: "Create request" }))

    await screen.findByText("Request detail page")
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === "POST")?.[1]
    expect(JSON.parse(String(post?.body))).toMatchObject({
      departmentId: "d-1",
      dueDate: "2030-05-17",
    })
  })

  it("shows a server-side date error under the due date", async () => {
    render("/requests/new", {
      [`POST /orgs/${ORG_ID}/requests`]: () =>
        jsonResponse(400, { status: 400, errors: { dueDate: ["must not be in the past"] } }),
    })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Title"), "Laptop")
    await user.selectOptions(screen.getByLabelText("Category"), "HR")
    await user.type(screen.getByLabelText("Due date"), "2020-01-01")
    await user.click(screen.getByRole("button", { name: "Create request" }))

    expect(await screen.findByText("must not be in the past")).toBeInTheDocument()
    expect(screen.queryByText("Request detail page")).not.toBeInTheDocument()
  })

  it("explains a department that was deactivated in the meantime", async () => {
    render("/requests/new", {
      [`POST /orgs/${ORG_ID}/requests`]: () =>
        jsonResponse(409, { status: 409, code: "DEPARTMENT_INACTIVE" }),
    })
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText("Title"), "Laptop")
    await user.selectOptions(screen.getByLabelText("Category"), "HR")
    await user.selectOptions(await screen.findByRole("combobox", { name: "Department" }), "d-1")
    await user.click(screen.getByRole("button", { name: "Create request" }))

    expect(
      await screen.findByText("This department is inactive. Choose another."),
    ).toBeInTheDocument()
  })
})

describe("RequestFormPage (edit)", () => {
  const requestKey = `GET /orgs/${ORG_ID}/requests/r-1`

  it("loads the request and saves changes with the version it saw", async () => {
    const fetchMock = render("/requests/r-1/edit", {
      [requestKey]: () => jsonResponse(200, detail()),
      [`PUT /orgs/${ORG_ID}/requests/r-1`]: () =>
        jsonResponse(200, detail({ title: "Renamed", version: 4 })),
    })
    const user = userEvent.setup()

    const title = await screen.findByLabelText("Title")
    await waitFor(() => expect(title).toHaveValue("Replace my laptop"))
    expect(screen.getByLabelText("Description")).toHaveValue("Screen is cracked")
    await user.clear(title)
    await user.type(title, "Renamed")
    await user.click(screen.getByRole("button", { name: "Save changes" }))

    expect(await screen.findByText("Request detail page")).toBeInTheDocument()
    const put = fetchMock.mock.calls.find(([, init]) => init?.method === "PUT")?.[1]
    expect(JSON.parse(String(put?.body))).toMatchObject({ title: "Renamed", version: 3 })
  })

  it("explains a conflict and reloads the request", async () => {
    let loads = 0
    render("/requests/r-1/edit", {
      [requestKey]: () => {
        loads += 1
        return jsonResponse(200, detail())
      },
      [`PUT /orgs/${ORG_ID}/requests/r-1`]: () =>
        jsonResponse(409, { status: 409, code: "STALE_VERSION" }),
    })
    const user = userEvent.setup()

    const title = await screen.findByLabelText("Title")
    await waitFor(() => expect(title).toHaveValue("Replace my laptop"))
    await user.type(title, " now")
    await user.click(screen.getByRole("button", { name: "Save changes" }))

    expect(await screen.findByText(/Someone else changed this request/)).toBeInTheDocument()
    await waitFor(() => expect(loads).toBeGreaterThanOrEqual(2))
  })

  it("does not offer a form for a request that cannot be edited", async () => {
    render("/requests/r-1/edit", {
      [requestKey]: () => jsonResponse(200, detail({ editable: false, status: "SUBMITTED" })),
    })

    expect(await screen.findByText("This request can't be edited")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Save changes" })).not.toBeInTheDocument()
  })

  it("shows one neutral screen for a request that is missing or not yours to see", async () => {
    render("/requests/r-1/edit", { [requestKey]: () => jsonResponse(404, { status: 404 }) })

    expect(await screen.findByText("Request not found")).toBeInTheDocument()
  })
})
