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

describe("RequestsPage starting filters from the URL", () => {
  it("starts with the status from the link and shows it in the selector", async () => {
    const fetchMock = render("MANAGER", "?status=SUBMITTED")

    expect(await screen.findByLabelText("Status")).toHaveValue("SUBMITTED")
    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("status=SUBMITTED"))
  })

  it("ignores a status it does not know", async () => {
    const fetchMock = render("MANAGER", "?status=NOPE")

    expect(await screen.findByLabelText("Status")).toHaveValue("")
    await waitFor(() => expect(lastListUrl(fetchMock)).not.toContain("status="))
  })

  it("starts with 'awaiting my review' for reviewers and locks the status selector", async () => {
    const fetchMock = render("MANAGER", "?reviewable=true")

    expect(await screen.findByLabelText("Awaiting my review")).toBeChecked()
    expect(screen.getByLabelText("Status")).toBeDisabled()
    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("reviewable=true"))
  })

  it("offers 'awaiting my review' only to reviewers and ignores the link for others", async () => {
    const fetchMock = render("EMPLOYEE", "?reviewable=true")

    await screen.findByLabelText("Status")
    expect(screen.queryByLabelText("Awaiting my review")).not.toBeInTheDocument()
    await waitFor(() => expect(lastListUrl(fetchMock)).not.toContain("reviewable"))
  })

  it("starts with 'only my requests' from the link", async () => {
    const fetchMock = render("MANAGER", "?mine=true")

    expect(await screen.findByLabelText("Only my requests")).toBeChecked()
    await waitFor(() => expect(lastListUrl(fetchMock)).toContain("mine=true"))
  })
})
