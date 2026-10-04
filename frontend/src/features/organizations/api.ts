import { z } from "zod"
import { apiFetch } from "@/shared/api/client"
import {
  myOrganizationSchema,
  organizationDetailSchema,
  type MyOrganization,
  type OrganizationDetail,
} from "./schemas"

export async function fetchMyOrganizations(): Promise<MyOrganization[]> {
  return z.array(myOrganizationSchema).parse(await apiFetch<unknown>("/orgs"))
}

export async function createOrganization(name: string): Promise<MyOrganization> {
  return myOrganizationSchema.parse(
    await apiFetch<unknown>("/orgs", { method: "POST", body: JSON.stringify({ name }) }),
  )
}

/** A tenant route: the server answers only if the caller is an active member. */
export async function fetchOrganization(orgId: string): Promise<OrganizationDetail> {
  return organizationDetailSchema.parse(
    await apiFetch<unknown>(`/orgs/${encodeURIComponent(orgId)}`),
  )
}
