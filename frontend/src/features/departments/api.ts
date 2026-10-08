import { apiFetch } from "@/shared/api/client"
import { buildQuery } from "@/shared/api/query"
import {
  departmentMemberPageSchema,
  departmentPageSchema,
  departmentSchema,
  type DepartmentFormValues,
} from "./schemas"

const base = (orgId: string) => `/orgs/${encodeURIComponent(orgId)}/departments`
const one = (orgId: string, id: string) => `${base(orgId)}/${encodeURIComponent(id)}`

export async function fetchDepartments(
  orgId: string,
  options: { page: number; size: number; active?: boolean | undefined; q?: string | undefined },
) {
  return departmentPageSchema.parse(await apiFetch<unknown>(`${base(orgId)}${buildQuery(options)}`))
}

export async function fetchDepartment(orgId: string, id: string) {
  return departmentSchema.parse(await apiFetch<unknown>(one(orgId, id)))
}

export async function createDepartment(orgId: string, values: DepartmentFormValues) {
  return departmentSchema.parse(
    await apiFetch<unknown>(base(orgId), { method: "POST", body: JSON.stringify(values) }),
  )
}

export async function updateDepartment(
  orgId: string,
  id: string,
  values: DepartmentFormValues,
  version: number,
) {
  return departmentSchema.parse(
    await apiFetch<unknown>(one(orgId, id), {
      method: "PUT",
      body: JSON.stringify({ ...values, version }),
    }),
  )
}

export async function setDepartmentActive(orgId: string, id: string, active: boolean) {
  return departmentSchema.parse(
    await apiFetch<unknown>(`${one(orgId, id)}/${active ? "reactivate" : "deactivate"}`, {
      method: "POST",
    }),
  )
}

export async function fetchDepartmentMembers(
  orgId: string,
  id: string,
  options: { page: number; size: number },
) {
  return departmentMemberPageSchema.parse(
    await apiFetch<unknown>(`${one(orgId, id)}/members${buildQuery(options)}`),
  )
}

export async function assignMember(orgId: string, id: string, membershipId: string) {
  await apiFetch<void>(`${one(orgId, id)}/members/${encodeURIComponent(membershipId)}`, {
    method: "PUT",
  })
}

export async function unassignMember(orgId: string, id: string, membershipId: string) {
  await apiFetch<void>(`${one(orgId, id)}/members/${encodeURIComponent(membershipId)}`, {
    method: "DELETE",
  })
}
