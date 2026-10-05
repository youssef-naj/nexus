import { z } from "zod"
import { apiFetch } from "@/shared/api/client"
import type { OrgRole } from "@/features/organizations/schemas"
import {
  acceptedInvitationSchema,
  invitationPreviewSchema,
  invitationSchema,
  memberPageSchema,
  roleChangeSchema,
} from "./schemas"

const orgPath = (orgId: string) => `/orgs/${encodeURIComponent(orgId)}`

export async function fetchMembers(
  orgId: string,
  options: { page: number; size: number; role?: OrgRole | undefined },
) {
  const query = new URLSearchParams({ page: String(options.page), size: String(options.size) })
  if (options.role) query.set("role", options.role)
  return memberPageSchema.parse(await apiFetch<unknown>(`${orgPath(orgId)}/members?${query}`))
}

export async function changeRole(
  orgId: string,
  membershipId: string,
  role: OrgRole,
  version: number,
) {
  return roleChangeSchema.parse(
    await apiFetch<unknown>(`${orgPath(orgId)}/members/${encodeURIComponent(membershipId)}`, {
      method: "PATCH",
      body: JSON.stringify({ role, version }),
    }),
  )
}

export async function removeMember(orgId: string, membershipId: string): Promise<void> {
  await apiFetch<void>(`${orgPath(orgId)}/members/${encodeURIComponent(membershipId)}`, {
    method: "DELETE",
  })
}

export async function leaveOrganization(orgId: string): Promise<void> {
  await apiFetch<void>(`${orgPath(orgId)}/leave`, { method: "POST" })
}

export async function fetchInvitations(orgId: string) {
  return z.array(invitationSchema).parse(await apiFetch<unknown>(`${orgPath(orgId)}/invitations`))
}

export async function createInvitation(orgId: string, email: string, role: OrgRole) {
  return invitationSchema.parse(
    await apiFetch<unknown>(`${orgPath(orgId)}/invitations`, {
      method: "POST",
      body: JSON.stringify({ email, role }),
    }),
  )
}

export async function revokeInvitation(orgId: string, invitationId: string): Promise<void> {
  await apiFetch<void>(`${orgPath(orgId)}/invitations/${encodeURIComponent(invitationId)}`, {
    method: "DELETE",
  })
}

// The token travels in the request body, never in a URL we send to the server.
export async function previewInvitation(token: string) {
  return invitationPreviewSchema.parse(
    await apiFetch<unknown>("/invitations/preview", {
      method: "POST",
      body: JSON.stringify({ token }),
    }),
  )
}

export async function acceptInvitation(token: string) {
  return acceptedInvitationSchema.parse(
    await apiFetch<unknown>("/invitations/accept", {
      method: "POST",
      body: JSON.stringify({ token }),
    }),
  )
}

export async function rejectInvitation(token: string): Promise<void> {
  await apiFetch<void>("/invitations/reject", { method: "POST", body: JSON.stringify({ token }) })
}
