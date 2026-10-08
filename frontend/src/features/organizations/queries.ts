import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { createOrganization, fetchMyOrganizations, fetchOrganization } from "./api"

export const organizationsQueryKey = ["organizations"] as const

/** Everything cached for one organization sits under ["organizations", orgId, ...]. */
export const orgKey = (orgId: string, ...parts: string[]) =>
  [...organizationsQueryKey, orgId, ...parts] as const

export function useMyOrganizations() {
  return useQuery({ queryKey: organizationsQueryKey, queryFn: fetchMyOrganizations })
}

export function useOrganization(orgId: string) {
  return useQuery({
    queryKey: [...organizationsQueryKey, orgId],
    queryFn: () => fetchOrganization(orgId),
    enabled: orgId.length > 0,
  })
}

export function useCreateOrganization() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createOrganization,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationsQueryKey }),
  })
}
