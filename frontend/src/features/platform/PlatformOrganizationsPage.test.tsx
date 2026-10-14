import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Route, Routes } from "react-router"
import { afterEach, describe, expect, it, vi } from "vitest"
import { pageOf, requestedUrls } from "@/test/orgFixtures"
import { jsonResponse, mockApi, renderWithProviders } from "@/test/utils"
import { PlatformOrganizationsPage } from "./PlatformOrganizationsPage"

afterEach(() => vi.unstubAllGlobals())

const listKey = "GET /platform/organizations"

function organization(overrides: Record<string, unknown> = {}) {
  return {
    id: "o-1",
    name: "Acme Corp",
    slug: "acme-corp",
    status: "ACTIVE",
    memberCount: 5,
    createdAt: "2026-10-01T10:00:00Z",
    version: 3,
    ...overrides,
  }
}

function render(extra: Record<string, () => Response> = {}, items = [organization()]) {
  const fetchMock = mockApi({ [listKey]: () => jsonResponse(200, pageOf(items)), ...extra })
  renderWithProviders(
    <Routes>
      <Route path="/platform" element={<PlatformOrganizationsPage />} />
    </Routes>,
    ["/platform"],
  )
  return fetchMock
}

const posts = (fetchMock: ReturnType<typeof mockApi>) =>
  fetchMock.mock.calls.filter(([, init]) => init?.method === "POST")
const lastListUrl = (fetchMock: ReturnType<typeof mockApi>) =>
  requestedUrls(fetchMock, "/platform/organizations?").at(-1) ?? ""

/**
 * The filter dropdown uses the same words as the table ("Active", "Suspended"), so wait for the
 * organization's name (only in the table) and then look inside its row.
 */
async function rowOf(name: string) {
  await screen.findByText(name)
  return screen.getByRole("row", { name: new RegExp(name) })
}

describe("PlatformOrganizationsPage", () => {
  it("lists organization metadata only", async () => {
    render()

    const row = await rowOf("Acme Corp")
    expect(within(row).getByText("acme-corp")).toBeInTheDocument()
    expect(within(row).getByText("5")).toBeInTheDocument()
    expect(within(row).getByText("Active")).toBeInTheDocument()
    expect(screen.getByText(/never its members' data/)).toBeInTheDocument()
  })

  it("suspends with a reason chosen from the suspension list, after a confirmation step", async () => {
    const fetchMock = render({
      "POST /platform/organizations/o-1/suspend": () =>
        jsonResponse(200, organization({ status: "SUSPENDED", version: 4 })),
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Suspend Acme Corp" }))
    const select = screen.getByLabelText("Reason for suspending Acme Corp")
    expect(
      within(select)
        .getAllByRole("option")
        .map((option) => option.textContent),
    ).toEqual([
      "Choose a reason…",
      "Policy violation",
      "Security concern",
      "Non-payment",
      "Customer request",
      "Other",
    ])
    expect(screen.getByRole("button", { name: "Confirm suspension of Acme Corp" })).toBeDisabled()
    expect(posts(fetchMock)).toHaveLength(0)
    await user.selectOptions(select, "NON_PAYMENT")
    await user.click(screen.getByRole("button", { name: "Confirm suspension of Acme Corp" }))

    expect(await screen.findByText("Acme Corp suspended.")).toBeInTheDocument()
    const post = posts(fetchMock)[0]?.[1]
    expect(JSON.parse(String(post?.body))).toEqual({ reason: "NON_PAYMENT", version: 3 })
    expect(new Headers(post?.headers).get("X-XSRF-TOKEN")).toBe("test-token")
  })

  it("reactivates a suspended organization with the reactivation reasons", async () => {
    const fetchMock = render(
      {
        "POST /platform/organizations/o-1/reactivate": () =>
          jsonResponse(200, organization({ status: "ACTIVE", version: 5 })),
      },
      [organization({ status: "SUSPENDED", version: 4 })],
    )
    const user = userEvent.setup()

    const row = await rowOf("Acme Corp")
    expect(within(row).getByText("Suspended")).toBeInTheDocument()
    await user.click(screen.getByRole("button", { name: "Reactivate Acme Corp" }))
    const select = screen.getByLabelText("Reason for reactivating Acme Corp")
    expect(within(select).getByRole("option", { name: "Issue resolved" })).toBeInTheDocument()
    expect(within(select).queryByRole("option", { name: "Non-payment" })).not.toBeInTheDocument()
    await user.selectOptions(select, "ISSUE_RESOLVED")
    await user.click(screen.getByRole("button", { name: "Confirm reactivation of Acme Corp" }))

    expect(await screen.findByText("Acme Corp reactivated.")).toBeInTheDocument()
    expect(JSON.parse(String(posts(fetchMock)[0]?.[1]?.body))).toEqual({
      reason: "ISSUE_RESOLVED",
      version: 4,
    })
  })

  it("lets the administrator cancel without sending anything", async () => {
    const fetchMock = render()
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Suspend Acme Corp" }))
    await user.click(screen.getByRole("button", { name: "Cancel" }))

    expect(screen.getByRole("button", { name: "Suspend Acme Corp" })).toBeInTheDocument()
    expect(posts(fetchMock)).toHaveLength(0)
  })

  it("explains a stale version and reloads the list", async () => {
    let loads = 0
    render({
      [listKey]: () => {
        loads += 1
        return jsonResponse(200, pageOf([organization()]))
      },
      "POST /platform/organizations/o-1/suspend": () =>
        jsonResponse(409, { status: 409, code: "STALE_VERSION" }),
    })
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Suspend Acme Corp" }))
    await user.selectOptions(screen.getByLabelText("Reason for suspending Acme Corp"), "OTHER")
    await user.click(screen.getByRole("button", { name: "Confirm suspension of Acme Corp" }))

    expect(await screen.findByText(/changed since you opened it/)).toBeInTheDocument()
    await waitFor(() => expect(loads).toBeGreaterThanOrEqual(2))
  })

  it("filters by status and searches, sending the values as query parameters", async () => {
    const fetchMock = render()
    const user = userEvent.setup()
    await screen.findByText("Acme Corp")

    await user.selectOptions(screen.getByLabelText("Status"), "SUSPENDED")
    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("status=SUSPENDED"))
    await user.type(screen.getByLabelText("Search organizations"), "100% & more")
    await user.click(screen.getByRole("button", { name: "Search" }))

    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("q=100%25+%26+more"))
  })

  it("moves between pages", async () => {
    const fetchMock = render({
      [listKey]: () =>
        jsonResponse(200, pageOf([organization()], { totalElements: 25, totalPages: 2 })),
    })
    const user = userEvent.setup()

    expect(await screen.findByText("Page 1 of 2")).toBeInTheDocument()
    await user.click(screen.getByRole("button", { name: "Next page" }))

    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("page=1"))
  })

  it("shows an empty state", async () => {
    render({}, [])
    expect(await screen.findByText("No organizations match.")).toBeInTheDocument()
  })

  it("recovers from a failed load", async () => {
    let calls = 0
    render({
      [listKey]: () =>
        ++calls === 1
          ? jsonResponse(500, { status: 500 })
          : jsonResponse(200, pageOf([organization()])),
    })

    expect(await screen.findByText("Couldn't load the organizations.")).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole("button", { name: "Try again" }))
    expect(await screen.findByText("Acme Corp")).toBeInTheDocument()
  })

  it("renders organization names as text, never as markup", async () => {
    render({}, [organization({ name: "<img src=x onerror=alert(1)>" })])

    expect(await screen.findByText("<img src=x onerror=alert(1)>")).toBeInTheDocument()
    expect(document.querySelector("img")).toBeNull()
  })
})
