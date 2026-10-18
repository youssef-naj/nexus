import { reactivateSuspendedOrganizations } from "./platform"
import { mkdirSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { request } from "@playwright/test"
import { BASE_URL, PASSWORD, PEOPLE, authFile } from "./session"


/** One login per demo account per run; the tests reuse the saved sessions. */
export default async function globalSetup(): Promise<void> {
  mkdirSync(fileURLToPath(new URL("../.auth/", import.meta.url)), { recursive: true })
  for (const [person, email] of Object.entries(PEOPLE)) {
    const context = await request.newContext({ baseURL: BASE_URL })
    await context.get("/api/system/ping")
    const token = (await context.storageState()).cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value
    if (!token) throw new Error(`No CSRF cookie was issued by ${BASE_URL}. Is the demo stack running?`)
    const response = await context.post("/api/auth/login", {
      form: { email, password: PASSWORD },
      headers: { "X-XSRF-TOKEN": token },
    })
    if (response.status() !== 204) {
      throw new Error(
        `Could not sign in ${email} (${response.status()}). Run scripts/seed-demo.mjs on a clean demo stack first.`,
      )
    }
    await context.storageState({ path: authFile(person as keyof typeof PEOPLE) })
    await context.dispose()
  }
  // A previous run that was interrupted may have left a demo organization suspended
  const healed = await reactivateSuspendedOrganizations()
  if (healed.length > 0) console.log(`Reactivated demo organizations left suspended: ${healed.join(", ")}`)
}
