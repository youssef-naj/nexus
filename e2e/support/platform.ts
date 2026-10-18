import { request } from "@playwright/test"
import { ATLAS, BASE_URL, NORTHWIND, authFile } from "./session"

const DEMO_ORGANIZATIONS = new Set([ATLAS, NORTHWIND])

/**
 * Reactivates the demo organizations if any of them is suspended, through the platform API and
 * independently of any browser. Only the two demo organizations are touched. Returns the names
 * that were reactivated; returns nothing when the platform operator role has not been granted.
 */
export async function reactivateSuspendedOrganizations(): Promise<string[]> {
  const context = await request.newContext({ baseURL: BASE_URL, storageState: authFile("platform") })
  try {
    const list = await context.get("/api/platform/organizations?status=SUSPENDED&size=100")
    if (list.status() !== 200) return []
    const page = (await list.json()) as { content: { id: string; name: string; version: number }[] }
    const token =
      (await context.storageState()).cookies.find((cookie) => cookie.name === "XSRF-TOKEN")?.value ?? ""
    const reactivated: string[] = []
    for (const organization of page.content.filter((entry) => DEMO_ORGANIZATIONS.has(entry.name))) {
      const response = await context.post(`/api/platform/organizations/${organization.id}/reactivate`, {
        data: { reason: "SUSPENDED_IN_ERROR", version: organization.version },
        headers: { "X-XSRF-TOKEN": token },
      })
      if (response.status() === 200) reactivated.push(organization.name)
    }
    return reactivated
  } finally {
    await context.dispose()
  }
}
