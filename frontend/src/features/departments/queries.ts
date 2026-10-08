import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { fetchMembers } from "@/features/members/api"
import { membersKey } from "@/features/members/queries"
import { orgKey } from "@/features/organizations/queries"
import {
  assignMember,
  createDepartment,
  fetchDepartment,
  fetchDepartmentMembers,
  fetchDepartments,
  setDepartmentActive,
  unassignMember,
  updateDepartment,
} from "./api"
import type { DepartmentFormValues } from "./schemas"

export const DEPARTMENTS_PAGE_SIZE = 20
const departmentsKey = (orgId: string) => orgKey(orgId, "departments")

export function useDepartments(
  orgId: string,
  filter: { page: number; active?: boolean | undefined; q?: string | undefined },
) {
  return useQuery({
    queryKey: [...departmentsKey(orgId), "list", filter],
    queryFn: () => fetchDepartments(orgId, { ...filter, size: DEPARTMENTS_PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })
}

/** Every department (active and inactive), for pickers. */
export function useAllDepartments(orgId: string) {
  return useQuery({
    queryKey: [...departmentsKey(orgId), "all"],
    queryFn: () => fetchDepartments(orgId, { page: 0, size: 100 }),
  })
}

export function useDepartment(orgId: string, id: string) {
  return useQuery({
    queryKey: [...departmentsKey(orgId), "detail", id],
    queryFn: () => fetchDepartment(orgId, id),
    enabled: id.length > 0,
  })
}

export function useDepartmentMembers(orgId: string, id: string, page: number) {
  return useQuery({
    queryKey: [...departmentsKey(orgId), "detail", id, "members", page],
    queryFn: () => fetchDepartmentMembers(orgId, id, { page, size: DEPARTMENTS_PAGE_SIZE }),
    enabled: id.length > 0,
    placeholderData: keepPreviousData,
  })
}

/** Candidates for assignment: the first 100 organization members. Assigning twice is harmless. */
export function useAssignableMembers(orgId: string, enabled: boolean) {
  return useQuery({
    queryKey: [...membersKey(orgId), "assignable"],
    queryFn: () => fetchMembers(orgId, { page: 0, size: 100 }),
    enabled,
  })
}

/** Department names appear in request lists, so renaming or deactivating refreshes those too. */
function useRefresh(orgId: string) {
  const queryClient = useQueryClient()
  return (alsoRequests: boolean) => {
    void queryClient.invalidateQueries({ queryKey: departmentsKey(orgId) })
    if (alsoRequests) {
      void queryClient.invalidateQueries({ queryKey: orgKey(orgId, "requests") })
    }
  }
}

export function useCreateDepartment(orgId: string) {
  const refresh = useRefresh(orgId)
  return useMutation({
    mutationFn: (values: DepartmentFormValues) => createDepartment(orgId, values),
    onSuccess: () => refresh(false),
  })
}

export function useUpdateDepartment(orgId: string) {
  const refresh = useRefresh(orgId)
  return useMutation({
    mutationFn: (change: { id: string; values: DepartmentFormValues; version: number }) =>
      updateDepartment(orgId, change.id, change.values, change.version),
    // Success or failure, reload: a conflict means our copy was stale
    onSettled: () => refresh(true),
  })
}

export function useSetDepartmentActive(orgId: string) {
  const refresh = useRefresh(orgId)
  return useMutation({
    mutationFn: (change: { id: string; active: boolean }) =>
      setDepartmentActive(orgId, change.id, change.active),
    onSettled: () => refresh(true),
  })
}

export function useAssignMember(orgId: string, departmentId: string) {
  const refresh = useRefresh(orgId)
  return useMutation({
    mutationFn: (membershipId: string) => assignMember(orgId, departmentId, membershipId),
    onSettled: () => refresh(false),
  })
}

export function useUnassignMember(orgId: string, departmentId: string) {
  const refresh = useRefresh(orgId)
  return useMutation({
    mutationFn: (membershipId: string) => unassignMember(orgId, departmentId, membershipId),
    onSettled: () => refresh(false),
  })
}
