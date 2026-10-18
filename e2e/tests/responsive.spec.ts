import { expect, test } from "@playwright/test"
import { ATLAS, authFile, orgIdByName, requestIdByTitle, settle } from "../support/session"

test.use({ storageState: authFile("amira") })

for (const width of [390, 640, 768]) {
  test(`nothing makes the page scroll sideways at ${width}px wide`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    const atlas = await orgIdByName(page, ATLAS)
    const vpn = await requestIdByTitle(page, atlas, "VPN is slow from the Casablanca office")
    const pages: [string, string][] = [
      ["organizations", "/"],
      ["dashboard", `/orgs/${atlas}`],
      ["requests", `/orgs/${atlas}/requests`],
      ["new request", `/orgs/${atlas}/requests/new`],
      ["request", `/orgs/${atlas}/requests/${vpn}`],
      ["departments", `/orgs/${atlas}/departments`],
      ["members", `/orgs/${atlas}/members`],
      ["audit", `/orgs/${atlas}/audit`],
    ]

    for (const [name, path] of pages) {
      await page.goto(path)
      await expect(page.locator("main h1").first()).toBeVisible()
      await settle(page)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      expect(overflow, `${name} at ${width}px`).toBeLessThanOrEqual(1)
    }
  })
}

test("a wide table scrolls inside its own labelled region on a phone", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const atlas = await orgIdByName(page, ATLAS)
  await page.goto(`/orgs/${atlas}/requests`)
  await settle(page)

  const region = page.getByRole("region", { name: "Requests" })
  await expect(region).toBeVisible()
  const scrolls = await region.evaluate((element) => element.scrollWidth > element.clientWidth)
  expect(scrolls).toBe(true)
  await region.focus()
  await expect(region).toBeFocused()
})
