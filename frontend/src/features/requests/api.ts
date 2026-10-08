import { apiFetch } from "@/shared/api/client"
import { buildQuery, type QueryValue } from "@/shared/api/query"
import { requestDetailSchema, requestPageSchema, type RequestPayload } from "./schemas"

const base = (orgId: string) => `/orgs/${encodeURIComponent(orgId)}/requests`

export type RequestListParams = Record<string, QueryValue>

export async function fetchRequests(orgId: string, params: RequestListParams) {
  return requestPageSchema.parse(await apiFetch<unknown>(`${base(orgId)}${buildQuery(params)}`))
}

export async function fetchRequest(orgId: string, requestId: string) {
  return requestDetailSchema.parse(
    await apiFetch<unknown>(`${base(orgId)}/${encodeURIComponent(requestId)}`),
  )
}

export async function createRequest(orgId: string, payload: RequestPayload) {
  return requestDetailSchema.parse(
    await apiFetch<unknown>(base(orgId), { method: "POST", body: JSON.stringify(payload) }),
  )
}

export async function updateRequest(
  orgId: string,
  requestId: string,
  payload: RequestPayload,
  version: number,
) {
  return requestDetailSchema.parse(
    await apiFetch<unknown>(`${base(orgId)}/${encodeURIComponent(requestId)}`, {
      method: "PUT",
      body: JSON.stringify({ ...payload, version }),
    }),
  )
}
