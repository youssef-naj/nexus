import { expect, test, type Page } from "@playwright/test"
import { expectAccessible } from "../support/a11y"
import {
  ATLAS,
  authFile,
  departmentIdByName,
  orgIdByName,
  requestIdByTitle,
  settle,
} from "../support/session"

async function ready(page: Page): Promise<void> {
  await expect(page.locator("main h1").first()).toBeVisible()
  await settle(page)
}

test.describe("signed out", () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  for (const [name, path] of [
    ["sign in", "/login"],
    ["create account", "/register"],
    ["page not found", "/no-such-page"],
  ] as const) {
    test(name, async ({ page }) => {
      await page.goto(path)
      await expect(page.locator("main, form, h1").first()).toBeVisible()
      await page.waitForLoadState("networkidle")
      await expectAccessible(page, name)
    })
  }
})

test.describe("an owner", () => {
  test.use({ storageState: authFile("amira") })

  type Ids = { atlas: string; vpn: string; operations: string }
  const screens: [string, (ids: Ids) => string][] = [
    ["organizations", () => "/"],
    ["dashboard with the review queues", (ids) => `/orgs/${ids.atlas}`],
    ["request list with filters", (ids) => `/orgs/${ids.atlas}/requests`],
    ["new request form", (ids) => `/orgs/${ids.atlas}/requests/new`],
    ["request under review (review and assignment panels, history)", (ids) => `/orgs/${ids.atlas}/requests/${ids.vpn}`],
    ["departments", (ids) => `/orgs/${ids.atlas}/departments`],
    ["department with members", (ids) => `/orgs/${ids.atlas}/departments/${ids.operations}`],
    ["members and invitations", (ids) => `/orgs/${ids.atlas}/members`],
    ["audit log", (ids) => `/orgs/${ids.atlas}/audit`],
  ]

  for (const [name, path] of screens) {
    test(name, async ({ page }) => {
      const atlas = await orgIdByName(page, ATLAS)
      const ids: Ids = {
        atlas,
        vpn: await requestIdByTitle(page, atlas, "VPN is slow from the Casablanca office"),
        operations: await departmentIdByName(page, atlas, "Operations"),
      }
      await page.goto(path(ids))
      await ready(page)
      await expectAccessible(page, name)
    })
  }
})

test.describe("a manager", () => {
  test.use({ storageState: authFile("priya") })

  test("dashboard with queues", async ({ page }) => {
    const atlas = await orgIdByName(page, ATLAS)
    await page.goto(`/orgs/${atlas}`)
    await ready(page)
    await expectAccessible(page, "manager dashboard")
  })
})

test.describe("an employee", () => {
  test.use({ storageState: authFile("sofia") })

  test("dashboard", async ({ page }) => {
    const atlas = await orgIdByName(page, ATLAS)
    await page.goto(`/orgs/${atlas}`)
    await ready(page)
    await expectAccessible(page, "employee dashboard")
  })

  test("own requests", async ({ page }) => {
    const atlas = await orgIdByName(page, ATLAS)
    await page.goto(`/orgs/${atlas}/requests`)
    await ready(page)
    await expectAccessible(page, "employee request list")
  })
})

test.describe("an employee editing a draft", () => {
  test.use({ storageState: authFile("hana") })

  test("edit form with existing values", async ({ page }) => {
    const atlas = await orgIdByName(page, ATLAS)
    const draft = await requestIdByTitle(page, atlas, "Plan the parental leave handover")
    await page.goto(`/orgs/${atlas}/requests/${draft}/edit`)
    await expect(page.getByLabel("Title")).toHaveValue("Plan the parental leave handover")
    await settle(page)
    await expectAccessible(page, "edit request form")
  })
})

test.describe("the platform operator", () => {
  test.use({ storageState: authFile("platform") })

  for (const [name, path] of [
    ["platform organizations", "/platform"],
    ["platform audit", "/platform/audit"],
  ] as const) {
    test(name, async ({ page }) => {
      const me = (await (await page.context().request.get("/api/auth/me")).json()) as { platformAdmin: boolean }
      test.skip(!me.platformAdmin, "Restart the backend once after seeding so the platform operator role is granted")
      await page.goto(path)
      await ready(page)
      await expectAccessible(page, name)
    })
  }
})

test.describe("on a phone", () => {
  test.use({ storageState: authFile("amira"), viewport: { width: 390, height: 844 } })

  for (const [name, path] of [
    ["dashboard", ""],
    ["request list", "/requests"],
    ["members", "/members"],
    ["audit log", "/audit"],
  ] as const) {
    test(name, async ({ page }) => {
      const atlas = await orgIdByName(page, ATLAS)
      await page.goto(`/orgs/${atlas}${path}`)
      await ready(page)
      await expectAccessible(page, `${name} at 390px`)
    })
  }
})
