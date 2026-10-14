import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { changeOrganizationStatus, fetchPlatformAudit, fetchPlatformOrganizations } from "./api"
import type { StatusAction } from "./schemas"

export const PLATFORM_PAGE_SIZE = 20
// Everything under "platform" is cleared on logout together with the rest of the cache
const platformKey = ["platform"] as const

export function usePlatformOrganizations(filter: {
  page: number
  status?: string | undefined
  q?: string | undefined
}) {
  return useQuery({
    queryKey: [...platformKey, "organizations", filter],
    queryFn: () => fetchPlatformOrganizations({ ...filter, size: PLATFORM_PAGE_SIZE }),
    placeholderData: keepPreviousData,
    retry: false,
  })
}

export function useChangeOrganizationStatus() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (change: {
      organizationId: string
      action: StatusAction
      reason: string
      version: number
    }) =>
      changeOrganizationStatus(change.organizationId, change.action, change.reason, change.version),
    // Success or failure, reload: a conflict means our copy was stale
    onSettled: () => queryClient.invalidateQueries({ queryKey: platformKey }),
  })
}

export function usePlatformAudit(filter: { page: number; eventType?: string | undefined }) {
  return useQuery({
    queryKey: [...platformKey, "audit", filter],
    queryFn: () => fetchPlatformAudit({ ...filter, size: PLATFORM_PAGE_SIZE }),
    placeholderData: keepPreviousData,
    retry: false,
  })
}
