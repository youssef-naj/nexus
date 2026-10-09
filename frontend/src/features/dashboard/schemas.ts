import { z } from "zod"
import { statusSchema } from "@/features/requests/schemas"

const countsSchema = z.record(z.string(), z.number())

export const dashboardSchema = z.object({
  /** What total and byStatus cover: the whole organization, or only your own requests. */
  scope: z.enum(["ORGANIZATION", "MINE"]),
  total: z.number(),
  byStatus: countsSchema,
  /** Null for people who cannot review. */
  awaitingReview: z.number().nullish(),
  mine: z.object({ total: z.number(), byStatus: countsSchema }),
  recent: z.array(
    z.object({
      id: z.string(),
      requestId: z.string(),
      reference: z.string(),
      title: z.string(),
      // A plain string, so an action this client does not know still displays
      action: z.string(),
      toStatus: statusSchema,
      actorName: z.string(),
      occurredAt: z.string(),
    }),
  ),
})
export type Dashboard = z.infer<typeof dashboardSchema>

export function countOf(counts: Record<string, number>, status: string): number {
  return counts[status] ?? 0
}
