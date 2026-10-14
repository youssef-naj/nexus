import { apiFetch } from "@/shared/api/client"
import { buildQuery } from "@/shared/api/query"
import {
  platformAuditPageSchema,
  platformOrganizationPageSchema,
  platformOrganizationSchema,
  type StatusAction,
} from "./schemas"

export async function fetchPlatformOrganizations(options: {
  page: number
  size: number
  status?: string | undefined
  q?: string | undefined
}) {
  return platformOrganizationPageSchema.parse(
    await apiFetch<unknown>(`/platform/organizations${buildQuery(options)}`),
  )
}

export async function changeOrganizationStatus(
  organizationId: string,
  action: StatusAction,
  reason: string,
  version: number,
) {
  return platformOrganizationSchema.parse(
    await apiFetch<unknown>(
      `/platform/organizations/${encodeURIComponent(organizationId)}/${action}`,
      { method: "POST", body: JSON.stringify({ reason, version }) },
    ),
  )
}

export async function fetchPlatformAudit(options: {
  page: number
  size: number
  eventType?: string | undefined
}) {
  return platformAuditPageSchema.parse(
    await apiFetch<unknown>(`/platform/audit${buildQuery(options)}`),
  )
}
