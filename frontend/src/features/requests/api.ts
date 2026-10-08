import { z } from "zod"
import { apiFetch } from "@/shared/api/client"
import { buildQuery, type QueryValue } from "@/shared/api/query"
import {
  requestDetailSchema,
  requestEventSchema,
  requestPageSchema,
  type RequestAction,
  type RequestPayload,
} from "./schemas"

const base = (orgId: string) => `/orgs/${encodeURIComponent(orgId)}/requests`
const one = (orgId: string, requestId: string) => `${base(orgId)}/${encodeURIComponent(requestId)}`

export type RequestListParams = Record<string, QueryValue>

export async function fetchRequests(orgId: string, params: RequestListParams) {
  return requestPageSchema.parse(await apiFetch<unknown>(`${base(orgId)}${buildQuery(params)}`))
}

export async function fetchRequest(orgId: string, requestId: string) {
  return requestDetailSchema.parse(await apiFetch<unknown>(one(orgId, requestId)))
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
    await apiFetch<unknown>(one(orgId, requestId), {
      method: "PUT",
      body: JSON.stringify({ ...payload, version }),
    }),
  )
}

export async function fetchRequestEvents(orgId: string, requestId: string) {
  return z
    .array(requestEventSchema)
    .parse(await apiFetch<unknown>(`${one(orgId, requestId)}/events`))
}

/** The version must be the one the user saw; comment is omitted when empty. */
export async function transitionRequest(
  orgId: string,
  requestId: string,
  action: RequestAction,
  version: number,
  comment: string | undefined,
) {
  return requestDetailSchema.parse(
    await apiFetch<unknown>(`${one(orgId, requestId)}/transitions`, {
      method: "POST",
      body: JSON.stringify({ action, version, comment }),
    }),
  )
}
