import { expect, type Page } from "@playwright/test"

export const PASSWORD = process.env.DEMO_PASSWORD ?? "Demo-passphrase-2026"
export const ATLAS = "Atlas Logistics"
export const NORTHWIND = "Northwind Studio"

/** The demo accounts created by scripts/seed-demo.mjs. */
export const PEOPLE = {
  amira: "amira.benali@demo.example", // Owner of Atlas, Manager in Northwind
  daniel: "daniel.okafor@demo.example", // Admin
  priya: "priya.raman@demo.example", // Manager
  lucas: "lucas.meyer@demo.example", // Manager
  sofia: "sofia.rossi@demo.example", // Employee
  tomas: "tomas.alvarez@demo.example", // Employee
  hana: "hana.kobayashi@demo.example", // Employee
  nora: "nora.lindqvist@demo.example", // Owner of Northwind
  platform: "platform@demo.example", // Platform operator
} as const

/**
 * Signs in through the API and shares the session with the browser context, so tests do not depend
 * on the wording of the sign-in form and only perform one login per person.
 */
export async function signIn(page: Page, email: string): Promise<void> {
  const api = page.context().request
  await api.get("/api/system/ping")
  const token = (await page.context().cookies()).find((cookie) => cookie.name === "XSRF-TOKEN")?.value
  expect(token, "the CSRF cookie was issued").toBeTruthy()
  const response = await api.post("/api/auth/login", {
    form: { email, password: PASSWORD },
    headers: { "X-XSRF-TOKEN": token as string },
  })
  expect(response.status(), `sign in as ${email}`).toBe(204)
}

export async function orgIdByName(page: Page, name: string): Promise<string> {
  const response = await page.context().request.get("/api/orgs")
  expect(response.status()).toBe(200)
  const organizations = (await response.json()) as { id: string; name: string }[]
  const found = organizations.find((organization) => organization.name === name)
  expect(found, `${name} is in the organization list`).toBeTruthy()
  return (found as { id: string }).id
}

export async function requestIdByTitle(page: Page, orgId: string, title: string): Promise<string> {
  const response = await page.context().request.get(
    `/api/orgs/${orgId}/requests?q=${encodeURIComponent(title)}&size=20`,
  )
  expect(response.status()).toBe(200)
  const result = (await response.json()) as { content: { id: string; title: string }[] }
  const found = result.content.find((request) => request.title === title)
  expect(found, `a request titled "${title}"`).toBeTruthy()
  return (found as { id: string }).id
}

/** Waits until the page has stopped loading: no pending requests and no loading placeholders. */
export async function settle(page: Page): Promise<void> {
  await page.waitForLoadState("networkidle")
  await expect(page.locator('[aria-label^="Loading"], [aria-busy="true"]')).toHaveCount(0)
}
