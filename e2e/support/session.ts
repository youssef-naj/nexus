import { fileURLToPath } from "node:url"
import { expect, type Page } from "@playwright/test"

export const BASE_URL = process.env.BASE_URL ?? "http://localhost:8088"
export const MAILPIT = (process.env.MAILPIT ?? "http://localhost:8026").replace(/\/$/, "")
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

export type Person = keyof typeof PEOPLE

/** Where the global setup saved this person's signed-in session. */
export function authFile(person: Person): string {
  return fileURLToPath(new URL(`../.auth/${person}.json`, import.meta.url))
}

export const unique = (): string => Date.now().toString(36)

export function futureDate(days: number): string {
  return new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10)
}

/** Signs in through the API and shares the session with the browser context. */
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

/** A state-changing API call with the CSRF header the browser would send. */
export async function apiSend(
  page: Page,
  method: "POST" | "PUT" | "DELETE",
  path: string,
  data?: unknown,
) {
  const token = (await page.context().cookies()).find((cookie) => cookie.name === "XSRF-TOKEN")?.value
  return page.context().request.fetch(path, {
    method,
    data,
    headers: { "X-XSRF-TOKEN": token ?? "" },
  })
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
  const response = await page
    .context()
    .request.get(`/api/orgs/${orgId}/requests?q=${encodeURIComponent(title)}&size=20`)
  expect(response.status()).toBe(200)
  const result = (await response.json()) as { content: { id: string; title: string }[] }
  const found = result.content.find((request) => request.title === title)
  expect(found, `a request titled "${title}"`).toBeTruthy()
  return (found as { id: string }).id
}

export async function departmentIdByName(page: Page, orgId: string, name: string): Promise<string> {
  const response = await page
    .context()
    .request.get(`/api/orgs/${orgId}/departments?q=${encodeURIComponent(name)}&size=20`)
  expect(response.status()).toBe(200)
  const result = (await response.json()) as { content: { id: string; name: string }[] }
  const found = result.content.find((department) => department.name === name)
  expect(found, `a department named "${name}"`).toBeTruthy()
  return (found as { id: string }).id
}

/** Waits until the page has stopped loading: no pending requests and no loading placeholders. */
export async function settle(page: Page): Promise<void> {
  await page.waitForLoadState("networkidle")
  await expect(page.locator('[aria-label^="Loading"], [aria-busy="true"]')).toHaveCount(0)
}

/** The token from the link in the newest email to this address, read from the demo mail catcher. */
export async function mailLinkToken(email: string, timeoutMs = 30_000): Promise<string> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const search = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)
    if (search.ok) {
      const found = (await search.json()) as { messages?: { ID: string }[] }
      for (const message of found.messages ?? []) {
        const full = await fetch(`${MAILPIT}/api/v1/message/${message.ID}`)
        if (!full.ok) continue
        const body = (await full.json()) as { Text?: string; HTML?: string }
        const match = /token=([A-Za-z0-9_.~-]+)/.exec(`${body.Text ?? ""}\n${body.HTML ?? ""}`)
        if (match?.[1]) return match[1]
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`No email with a link arrived for ${email} (${MAILPIT})`)
}
