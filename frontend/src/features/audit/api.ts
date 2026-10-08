import { apiFetch } from "@/shared/api/client"
import { buildQuery } from "@/shared/api/query"
import { auditPageSchema } from "./schemas"

export async function fetchAudit(
  orgId: string,
  options: {
    page: number
    size: number
    eventType?: string | undefined
    from?: string | undefined
    to?: string | undefined
  },
) {
  return auditPageSchema.parse(
    await apiFetch<unknown>(`/orgs/${encodeURIComponent(orgId)}/audit${buildQuery(options)}`),
  )
}
