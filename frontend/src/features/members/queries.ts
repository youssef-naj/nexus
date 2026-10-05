import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { organizationsQueryKey } from "@/features/organizations/queries"
import type { OrgRole } from "@/features/organizations/schemas"
import {
  acceptInvitation,
  changeRole,
  createInvitation,
  fetchInvitations,
  fetchMembers,
  leaveOrganization,
  previewInvitation,
  rejectInvitation,
  removeMember,
  revokeInvitation,
} from "./api"
import type { InviteForm } from "./schemas"

export const PAGE_SIZE = 20

// Everything cached for one organization sits under ["organizations", orgId, ...]
export const membersKey = (orgId: string) => [...organizationsQueryKey, orgId, "members"] as const
export const invitationsKey = (orgId: string) =>
  [...organizationsQueryKey, orgId, "invitations"] as const

export function useMembers(orgId: string, page: number, role?: OrgRole | undefined) {
  return useQuery({
    queryKey: [...membersKey(orgId), page, role ?? "all"],
    queryFn: () => fetchMembers(orgId, { page, size: PAGE_SIZE, role }),
    placeholderData: keepPreviousData,
  })
}

export function useChangeRole(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (change: { membershipId: string; role: OrgRole; version: number }) =>
      changeRole(orgId, change.membershipId, change.role, change.version),
    // Success or failure, reload: a conflict means our copy of the list was stale
    onSettled: () => queryClient.invalidateQueries({ queryKey: membersKey(orgId) }),
  })
}

export function useRemoveMember(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (membershipId: string) => removeMember(orgId, membershipId),
    onSettled: () => queryClient.invalidateQueries({ queryKey: membersKey(orgId) }),
  })
}

export function useLeaveOrganization(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => leaveOrganization(orgId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationsQueryKey }),
  })
}

export function useInvitations(orgId: string) {
  return useQuery({ queryKey: invitationsKey(orgId), queryFn: () => fetchInvitations(orgId) })
}

export function useCreateInvitation(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (values: InviteForm) => createInvitation(orgId, values.email, values.role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: invitationsKey(orgId) }),
  })
}

export function useRevokeInvitation(orgId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (invitationId: string) => revokeInvitation(orgId, invitationId),
    onSettled: () => queryClient.invalidateQueries({ queryKey: invitationsKey(orgId) }),
  })
}

export function useInvitationPreview(token: string | null) {
  return useQuery({
    queryKey: ["invitation", "preview", token],
    queryFn: () => previewInvitation(token ?? ""),
    enabled: Boolean(token),
    retry: false,
  })
}

export function useAcceptInvitation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: acceptInvitation,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: organizationsQueryKey }),
  })
}

export function useRejectInvitation() {
  return useMutation({ mutationFn: rejectInvitation })
}
