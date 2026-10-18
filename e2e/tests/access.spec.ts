import { expect, test } from "../support/test"
import {
  ATLAS,
  NORTHWIND,
  apiSend,
  orgIdByName,
  requestIdByTitle,
  unique,
} from "../support/session"

test("an employee cannot see a colleague's request, not even by its address", async ({ cast }) => {
  const sofia = await cast.open("sofia")
  const tomas = await cast.open("tomas")
  const atlas = await orgIdByName(sofia, ATLAS)
  const deskId = await requestIdByTitle(sofia, atlas, "Request a standing desk")

  await tomas.goto(`/orgs/${atlas}/requests/${deskId}`)
  await expect(tomas.getByText("Request not found")).toBeVisible()
  const api = await tomas.context().request.get(`/api/orgs/${atlas}/requests/${deskId}`)
  expect(api.status()).toBe(404)

  await tomas.goto(`/orgs/${atlas}/requests`)
  await expect(tomas.getByText("You see the requests you created.")).toBeVisible()
  await tomas.getByLabel("Search requests").fill("standing desk")
  await tomas.getByRole("button", { name: "Search" }).click()
  await expect(tomas.getByText("No requests match these filters.")).toBeVisible()
})

test("organizations are isolated: another organization's request and pages look like they do not exist", async ({ cast }) => {
  const nora = await cast.open("nora")
  const amira = await cast.open("amira")
  const sofia = await cast.open("sofia")
  const northwind = await orgIdByName(nora, NORTHWIND)
  const atlas = await orgIdByName(amira, ATLAS)
  const northwindRequest = await requestIdByTitle(amira, northwind, "Print budget for the brand refresh")

  // Amira belongs to both, but a Northwind request is not reachable through Atlas
  await amira.goto(`/orgs/${atlas}/requests/${northwindRequest}`)
  await expect(amira.getByText("Request not found")).toBeVisible()
  const mixed = await amira.context().request.get(`/api/orgs/${atlas}/requests/${northwindRequest}`)
  expect(mixed.status()).toBe(404)

  // Sofia belongs to Atlas only: Northwind answers exactly like an address that never existed
  await sofia.goto(`/orgs/${northwind}`)
  await expect(sofia.getByText(/not found/i).first()).toBeVisible()
  expect((await sofia.context().request.get(`/api/orgs/${northwind}`)).status()).toBe(404)
  expect((await sofia.context().request.get(`/api/orgs/${northwind}/requests`)).status()).toBe(404)
})

test("what each role is shown follows what the server allows", async ({ cast }) => {
  const atlas = await orgIdByName(await cast.open("amira"), ATLAS)
  const cases = [
    { person: "amira", audit: true, queue: true },
    { person: "daniel", audit: true, queue: true },
    { person: "priya", audit: false, queue: true },
    { person: "sofia", audit: false, queue: false },
  ] as const

  for (const { person, audit, queue } of cases) {
    const page = await cast.open(person)
    await page.goto(`/orgs/${atlas}`)
    const navigation = page.getByRole("navigation", { name: "Organization" })
    await expect(navigation.getByRole("link", { name: "Requests" }), person).toBeVisible()
    await expect(navigation.getByRole("link", { name: "Audit" }), person).toHaveCount(audit ? 1 : 0)
    await expect(page.getByRole("heading", { name: "Awaiting your review" }), person).toHaveCount(queue ? 1 : 0)
  }
})

test("an employee's dashboard is about their own requests", async ({ cast }) => {
  const sofia = await cast.open("sofia")
  const atlas = await orgIdByName(sofia, ATLAS)

  await sofia.goto(`/orgs/${atlas}`)

  await expect(sofia.getByRole("heading", { name: "Your requests" })).toBeVisible()
  await expect(sofia.getByRole("heading", { name: "Recent activity" })).toBeVisible()
  await expect(sofia.getByText(/in total/)).toHaveCount(0)
})

test("nobody can review their own request, whatever their role", async ({ cast }) => {
  const amira = await cast.open("amira")
  const atlas = await orgIdByName(amira, ATLAS)
  const own = await requestIdByTitle(amira, atlas, "Hire two warehouse operators")

  await amira.goto(`/orgs/${atlas}/requests/${own}`)

  await expect(amira.getByRole("heading", { name: "Hire two warehouse operators" })).toBeVisible()
  await expect(amira.getByRole("button", { name: "Approve" })).toHaveCount(0)
  await expect(amira.getByRole("button", { name: "Reject" })).toHaveCount(0)
  await expect(amira.getByRole("button", { name: "Request changes" })).toHaveCount(0)
})

test("the audit log is refused to a manager, even by address", async ({ cast }) => {
  const priya = await cast.open("priya")
  const atlas = await orgIdByName(priya, ATLAS)

  await priya.goto(`/orgs/${atlas}/audit`)

  await expect(priya.getByText("You don't have permission to view the audit log.")).toBeVisible()
})

test("two reviewers deciding on the same request: the second one is told it was already handled", async ({ cast }) => {
  const title = `E2E conflict ${unique()}`
  const sofia = await cast.open("sofia")
  const atlas = await orgIdByName(sofia, ATLAS)
  const created = await apiSend(sofia, "POST", `/api/orgs/${atlas}/requests`, { title, category: "OTHER" })
  expect(created.status()).toBe(201)
  const { id } = (await created.json()) as { id: string }
  expect((await apiSend(sofia, "POST", `/api/orgs/${atlas}/requests/${id}/transitions`, { action: "SUBMIT", version: 0 })).status()).toBe(200)

  const priya = await cast.open("priya")
  const lucas = await cast.open("lucas")
  await priya.goto(`/orgs/${atlas}/requests/${id}`)
  await lucas.goto(`/orgs/${atlas}/requests/${id}`)
  await expect(priya.getByRole("button", { name: "Approve" })).toBeVisible()
  await expect(lucas.getByRole("button", { name: "Approve" })).toBeVisible()

  await priya.getByRole("button", { name: "Approve" }).click()
  await expect(priya.getByText("Request approved.")).toBeVisible()

  await lucas.getByRole("button", { name: "Approve" }).click()
  await expect(lucas.getByText(/already handled/)).toBeVisible()
})
