import { z } from "zod"
import { pageOf } from "@/shared/api/page"

export const auditEntrySchema = z.object({
  id: z.string(),
  // A plain string, so an event type this client does not know yet still displays
  eventType: z.string(),
  actorUserId: z.string().nullish(),
  actorName: z.string().nullish(),
  targetType: z.string().nullish(),
  targetId: z.string().nullish(),
  metadata: z.record(z.string(), z.string()),
  occurredAt: z.string(),
})
export type AuditEntry = z.infer<typeof auditEntrySchema>
export const auditPageSchema = pageOf(auditEntrySchema)
