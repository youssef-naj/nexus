import { z } from "zod"
import { roleSchema } from "@/features/organizations/schemas"
import { pageOf } from "@/shared/api/page"
import { hasControlCharacter, hasDisallowedControlCharacter } from "@/shared/forms/text"

export const departmentSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullish(),
  active: z.boolean(),
  version: z.number(),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type Department = z.infer<typeof departmentSchema>

export const departmentPageSchema = pageOf(departmentSchema)

export const departmentMemberSchema = z.object({
  membershipId: z.string(),
  userId: z.string(),
  displayName: z.string(),
  // Only returned to roles that may manage members
  email: z.string().nullish(),
  role: roleSchema,
  assignedAt: z.string(),
})
export type DepartmentMember = z.infer<typeof departmentMemberSchema>

export const departmentMemberPageSchema = pageOf(departmentMemberSchema)

export const departmentFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Enter a name for the department.")
    .max(80, "Name is too long (maximum 80 characters).")
    .refine((value) => !hasControlCharacter(value), "Name must not contain control characters."),
  description: z
    .string()
    .max(500, "Description is too long (maximum 500 characters).")
    .refine(
      (value) => !hasDisallowedControlCharacter(value),
      "Description must not contain control characters.",
    ),
})
export type DepartmentFormValues = z.infer<typeof departmentFormSchema>
