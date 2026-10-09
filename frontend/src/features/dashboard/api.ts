import { apiFetch } from "@/shared/api/client"
import { dashboardSchema } from "./schemas"

export async function fetchDashboard(orgId: string) {
  return dashboardSchema.parse(
    await apiFetch<unknown>(`/orgs/${encodeURIComponent(orgId)}/dashboard`),
  )
}
