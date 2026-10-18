import { expect, test } from "@playwright/test"
import { PASSWORD, PEOPLE, mailLinkToken, unique } from "../support/session"

test("a new person registers, verifies by email, signs in and signs out", async ({ page }) => {
  const email = `e2e-${unique()}@demo.example`

  await page.goto("/register")
  await page.getByLabel(/name/i).fill("E2E Tester")
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD)
  await page.getByRole("button", { name: /create account|register|sign up/i }).click()
  await expect(page.getByText(/check your (email|inbox)|verif/i).first()).toBeVisible()

  // The link in the real email, opened like a person would
  const token = await mailLinkToken(email)
  await page.goto(`/verify-email?token=${encodeURIComponent(token)}`)
  await page.waitForLoadState("networkidle")
  const confirm = page.getByRole("button", { name: /verify|confirm/i })
  if (await confirm.isVisible()) await confirm.click()
  await expect(page.getByText(/verified|confirmed|success/i).first()).toBeVisible()

  await page.goto("/login")
  await page.getByLabel(/email/i).fill(email)
  await page.getByLabel("Password", { exact: true }).fill(PASSWORD)
  await page.getByRole("button", { name: /sign in|log in/i }).click()
  await expect(page).toHaveURL(/\/$/)

  await page.getByRole("button", { name: /sign out|log out/i }).click()
  await expect(page).toHaveURL(/\/login/)
  await page.goto("/")
  await expect(page).toHaveURL(/\/login/)
})

test("a wrong password gives a generic error and keeps you on the sign-in page", async ({ page }) => {
  await page.goto("/login")
  await page.getByLabel(/email/i).fill(PEOPLE.sofia)
  await page.getByLabel("Password", { exact: true }).fill("definitely-not-the-password")
  await page.getByRole("button", { name: /sign in|log in/i }).click()

  await expect(page.getByRole("alert")).toBeVisible()
  await expect(page).toHaveURL(/\/login/)
})

test("protected pages send visitors who are not signed in to the sign-in page", async ({ page }) => {
  await page.goto("/orgs/00000000-0000-0000-0000-000000000000/requests")

  await expect(page).toHaveURL(/\/login/)
})
