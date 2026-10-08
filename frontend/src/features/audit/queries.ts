import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { orgKey } from "@/features/organizations/queries"
import { fetchAudit } from "./api"

export const AUDIT_PAGE_SIZE = 20

export function useAudit(
  orgId: string,
  filter: {
    page: number
    eventType?: string | undefined
    from?: string | undefined
    to?: string | undefined
  },
) {
  return useQuery({
    queryKey: [...orgKey(orgId, "audit"), filter],
    queryFn: () => fetchAudit(orgId, { ...filter, size: AUDIT_PAGE_SIZE }),
    placeholderData: keepPreviousData,
    retry: false,
  })
}
