import { expect, test, type Locator, type Page } from "@playwright/test"
import { ATLAS, authFile, orgIdByName, settle } from "../support/session"

test.use({ storageState: authFile("amira") })

async function tabTo(page: Page, target: Locator, limit = 60): Promise<void> {
  for (let i = 0; i < limit; i++) {
    await page.keyboard.press("Tab")
    if (await target.evaluate((element) => element === document.activeElement)) return
  }
  throw new Error("The element cannot be reached with the Tab key")
}

async function showsFocus(target: Locator): Promise<boolean> {
  return target.evaluate((element) => {
    const style = getComputedStyle(element)
    const outline = style.outlineStyle !== "none" && Number.parseFloat(style.outlineWidth) > 0
    return outline || style.boxShadow !== "none"
  })
}

test("the skip link is the first stop and moves focus to the content", async ({ page }) => {
  const atlas = await orgIdByName(page, ATLAS)
  await page.goto(`/orgs/${atlas}`)
  await settle(page)

  await page.keyboard.press("Tab")
  const skip = page.getByRole("link", { name: /skip to (main )?content/i })
  await expect(skip).toBeFocused()
  await page.keyboard.press("Enter")

  await expect(page.locator("#main")).toBeFocused()
})

test("navigating with the keyboard changes the page title and puts focus in the new content", async ({ page }) => {
  const atlas = await orgIdByName(page, ATLAS)
  await page.goto(`/orgs/${atlas}`)
  await settle(page)
  await expect(page).toHaveTitle("Dashboard · Nexus")
  const navigation = page.getByRole("navigation", { name: "Organization" })

  for (const [link, title] of [
    ["Requests", "Requests · Nexus"],
    ["Departments", "Departments · Nexus"],
    ["Members", "Members · Nexus"],
    ["Audit", "Audit log · Nexus"],
  ] as const) {
    await navigation.getByRole("link", { name: link }).focus()
    await page.keyboard.press("Enter")
    await expect(page, link).toHaveTitle(title)
    await expect(page.locator("#main"), link).toBeFocused()
  }
})

test("the form fields follow the visual order and every field has a label", async ({ page }) => {
  const atlas = await orgIdByName(page, ATLAS)
  await page.goto(`/orgs/${atlas}/requests/new`)
  await settle(page)

  await page.getByLabel("Title").focus()
  for (const label of ["Description", "Category", "Department", "Due date"]) {
    await page.keyboard.press("Tab")
    await expect(page.getByLabel(label), label).toBeFocused()
  }
})

test("controls reached with the keyboard show where the focus is", async ({ page }) => {
  const atlas = await orgIdByName(page, ATLAS)
  await page.goto(`/orgs/${atlas}`)
  await settle(page)

  await page.keyboard.press("Tab")
  expect(await showsFocus(page.getByRole("link", { name: /skip to (main )?content/i })), "skip link").toBe(true)

  const requests = page.getByRole("navigation", { name: "Organization" }).getByRole("link", { name: "Requests" })
  await tabTo(page, requests)
  expect(await showsFocus(requests), "navigation link").toBe(true)
})
