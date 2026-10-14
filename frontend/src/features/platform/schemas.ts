import { z } from "zod"
import { orgStatusSchema } from "@/features/organizations/schemas"
import { pageOf } from "@/shared/api/page"

export const platformOrganizationSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  status: orgStatusSchema,
  memberCount: z.number(),
  createdAt: z.string(),
  version: z.number(),
})
export type PlatformOrganization = z.infer<typeof platformOrganizationSchema>
export const platformOrganizationPageSchema = pageOf(platformOrganizationSchema)

export const platformAuditEntrySchema = z.object({
  id: z.string(),
  // A plain string, so an event type this client does not know yet still displays
  eventType: z.string(),
  actorName: z.string().nullish(),
  targetType: z.string().nullish(),
  targetName: z.string().nullish(),
  metadata: z.record(z.string(), z.string()),
  occurredAt: z.string(),
})
export const platformAuditPageSchema = pageOf(platformAuditEntrySchema)

/** The fixed reason codes the server accepts for each action (it checks them again). */
export const SUSPEND_REASONS = [
  "POLICY_VIOLATION",
  "SECURITY_CONCERN",
  "NON_PAYMENT",
  "CUSTOMER_REQUEST",
  "OTHER",
] as const
export const REACTIVATE_REASONS = [
  "ISSUE_RESOLVED",
  "SUSPENDED_IN_ERROR",
  "CUSTOMER_REQUEST",
  "OTHER",
] as const

export type StatusAction = "suspend" | "reactivate"
