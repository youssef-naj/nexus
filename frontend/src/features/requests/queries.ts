import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { orgKey } from "@/features/organizations/queries"
import {
  createRequest,
  fetchRequest,
  fetchRequestEvents,
  fetchRequests,
  transitionRequest,
  updateRequest,
  type RequestListParams,
} from "./api"
import type { RequestAction, RequestPayload } from "./schemas"

export const REQUESTS_PAGE_SIZE = 20
const requestsKey = (orgId: string) => orgKey(orgId, "requests")

export function useRequests(orgId: string, params: RequestListParams) {
  return useQuery({
    queryKey: [...requestsKey(orgId), "list", params],
    queryFn: () => fetchRequests(orgId, { ...params, size: REQUESTS_PAGE_SIZE }),
    placeholderData: keepPreviousData,
  })
}

export function useRequest(orgId: string, requestId: string) {
  return useQuery({
    queryKey: [...requestsKey(orgId), "detail", requestId],
    queryFn: () => fetchRequest(orgId, requestId),
    enabled: requestId.length > 0,
  })
}

export function useRequestEvents(orgId: string, requestId: string) {
  return useQuery({
    queryKey: [...requestsKey(orgId), "events", requestId],
    queryFn: () => fetchRequestEvents(orgId, requestId),
    enabled: requestId.length > 0,
  })
}

export function useCreateRequest(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: RequestPayload) => createRequest(orgId, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: requestsKey(orgId) }),
  })
}

export function useUpdateRequest(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (change: { requestId: string; payload: RequestPayload; version: number }) =>
      updateRequest(orgId, change.requestId, change.payload, change.version),
    // Success or failure, reload: a conflict means our copy was stale
    onSettled: () => queryClient.invalidateQueries({ queryKey: requestsKey(orgId) }),
  })
}

export function useTransitionRequest(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (change: {
      requestId: string
      action: RequestAction
      version: number
      comment: string | undefined
    }) => transitionRequest(orgId, change.requestId, change.action, change.version, change.comment),
    // Success or failure, reload detail, list and history: a conflict means we were stale
    onSettled: () => queryClient.invalidateQueries({ queryKey: requestsKey(orgId) }),
  })
}
