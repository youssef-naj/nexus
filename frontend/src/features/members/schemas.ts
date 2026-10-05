import { z } from "zod"
import { roleSchema } from "@/features/organizations/schemas"

export const memberSchema = z.object({
  id: z.string(),
  userId: z.string(),
  displayName: z.string(),
  // Only returned to roles that may manage members
  email: z.string().nullish(),
  role: roleSchema,
  joinedAt: z.string(),
  version: z.number(),
  you: z.boolean(),
})
export type Member = z.infer<typeof memberSchema>

export const memberPageSchema = z.object({
  content: z.array(memberSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
})

export const roleChangeSchema = z.object({ id: z.string(), role: roleSchema, version: z.number() })

export const invitationSchema = z.object({
  id: z.string(),
  email: z.string(),
  role: roleSchema,
  createdAt: z.string(),
  expiresAt: z.string(),
  expired: z.boolean(),
})
export type Invitation = z.infer<typeof invitationSchema>

export const invitationPreviewSchema = z.object({
  organizationName: z.string(),
  role: roleSchema,
})

export const acceptedInvitationSchema = z.object({
  organizationId: z.string(),
  organizationName: z.string(),
  role: roleSchema,
})

export const inviteSchema = z.object({
  email: z
    .string()
    .trim()
    .pipe(z.email("Enter a valid email address.").max(320, "Email is too long.")),
  role: roleSchema,
})
export type InviteForm = z.infer<typeof inviteSchema>
