import { z } from "zod"
import { pageOf } from "@/shared/api/page"
import { hasControlCharacter, hasDisallowedControlCharacter } from "@/shared/forms/text"

export const CATEGORIES = ["IT_SUPPORT", "FACILITIES", "HR", "FINANCE", "OTHER"] as const
export const STATUSES = ["DRAFT", "SUBMITTED", "CHANGES_REQUESTED", "APPROVED", "REJECTED"] as const

export const categorySchema = z.enum(CATEGORIES)
export type RequestCategory = z.infer<typeof categorySchema>
export const statusSchema = z.enum(STATUSES)
export type RequestStatus = z.infer<typeof statusSchema>

export const requestSummarySchema = z.object({
  id: z.string(),
  reference: z.string(),
  title: z.string(),
  category: categorySchema,
  status: statusSchema,
  createdByMembershipId: z.string(),
  createdByName: z.string(),
  departmentId: z.string().nullish(),
  departmentName: z.string().nullish(),
  dueDate: z.string().nullish(),
  createdAt: z.string(),
  updatedAt: z.string(),
  version: z.number(),
})
export type RequestSummary = z.infer<typeof requestSummarySchema>
export const requestPageSchema = pageOf(requestSummarySchema)

export const requestDetailSchema = requestSummarySchema.extend({
  description: z.string().nullish(),
  assigneeMembershipId: z.string().nullish(),
  assigneeName: z.string().nullish(),
  /** Whether THIS viewer may edit it now (creator, and a draft or sent back). */
  editable: z.boolean(),
})
export type RequestDetail = z.infer<typeof requestDetailSchema>

export const requestFormSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "Enter a title.")
    .max(150, "Title is too long (maximum 150 characters).")
    .refine((value) => !hasControlCharacter(value), "Title must not contain control characters."),
  description: z
    .string()
    .max(5000, "Description is too long (maximum 5000 characters).")
    .refine(
      (value) => !hasDisallowedControlCharacter(value),
      "Description must not contain control characters.",
    ),
  category: z
    .union([categorySchema, z.literal("")])
    // The explicit `boolean` return type matters: without it TypeScript infers a
    // type guard (`value is RequestCategory`) and Zod narrows the output type, so
    // the form could no longer hold "" before the user picks a category.
    .refine((value): boolean => value !== "", "Choose a category."),
  dueDate: z.string().regex(/^(\d{4}-\d{2}-\d{2})?$/, "Enter a valid date."),
  departmentId: z.string(),
})
export type RequestFormValues = z.infer<typeof requestFormSchema>

/** What the API accepts: empty optional fields are null. */
export interface RequestPayload {
  title: string
  description: string
  category: RequestCategory
  dueDate: string | null
  departmentId: string | null
}
