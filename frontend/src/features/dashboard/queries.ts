import { useQuery } from "@tanstack/react-query"
import { orgKey } from "@/features/organizations/queries"
import { fetchDashboard } from "./api"

/** Always refetched when the page opens: the figures change whenever anyone acts on a request. */
export function useDashboard(orgId: string) {
  return useQuery({
    queryKey: orgKey(orgId, "dashboard"),
    queryFn: () => fetchDashboard(orgId),
    staleTime: 0,
  })
}
