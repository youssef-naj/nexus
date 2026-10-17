import { mkdirSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { expect, test, type Page } from "@playwright/test"
import { ATLAS, PEOPLE, orgIdByName, requestIdByTitle, settle, signIn } from "../support/session"

const folder = new URL("../../docs/screenshots/", import.meta.url)

test.beforeAll(() => {
  mkdirSync(fileURLToPath(folder), { recursive: true })
})

async function shoot(page: Page, name: string): Promise<void> {
  await settle(page)
  await page.screenshot({
    path: fileURLToPath(new URL(`${name}.png`, folder)),
    fullPage: true,
    animations: "disabled",
  })
}

test("the sign-in page", async ({ page }) => {
  await page.goto("/login")
  await shoot(page, "01-sign-in")
})

test("an owner: organizations, a request with its history, departments, members and the audit log", async ({ page }) => {
  await signIn(page, PEOPLE.amira)
  const atlas = await orgIdByName(page, ATLAS)

  await page.goto("/")
  await shoot(page, "02-organizations")

  const approved = await requestIdByTitle(page, atlas, "Replace the cracked laptop screen")
  await page.goto(`/orgs/${atlas}/requests/${approved}`)
  await shoot(page, "06-request-history")

  await page.goto(`/orgs/${atlas}/departments`)
  await shoot(page, "07-departments")
  await page.goto(`/orgs/${atlas}/members`)
  await shoot(page, "08-members")
  await page.goto(`/orgs/${atlas}/audit`)
  await shoot(page, "09-audit")
})

test("a manager: dashboard, request list, a request to review, and the narrow layout", async ({ page }) => {
  await signIn(page, PEOPLE.priya)
  const atlas = await orgIdByName(page, ATLAS)

  await page.goto(`/orgs/${atlas}`)
  await shoot(page, "03-dashboard-reviewer")

  await page.goto(`/orgs/${atlas}/requests`)
  await shoot(page, "04-requests")

  const awaiting = await requestIdByTitle(page, atlas, "VPN is slow from the Casablanca office")
  await page.goto(`/orgs/${atlas}/requests/${awaiting}`)
  await shoot(page, "05-request-review")

  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`/orgs/${atlas}`)
  await shoot(page, "12-mobile-dashboard")
})

test("an employee sees only their own requests", async ({ page }) => {
  await signIn(page, PEOPLE.sofia)
  const atlas = await orgIdByName(page, ATLAS)

  await page.goto(`/orgs/${atlas}`)
  await shoot(page, "10-dashboard-employee")
})

test("the platform operator sees organizations as metadata only", async ({ page }) => {
  await signIn(page, PEOPLE.platform)
  const me = (await (await page.context().request.get("/api/auth/me")).json()) as { platformAdmin: boolean }
  test.skip(!me.platformAdmin, "Restart the backend once so the platform operator role is granted")

  await page.goto("/platform")
  await expect(page.getByRole("heading", { name: "Organizations" })).toBeVisible()
  await shoot(page, "11-platform")
})
