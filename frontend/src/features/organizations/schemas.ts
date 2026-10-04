import { z } from "zod"

export const ROLES = ["OWNER", "ADMIN", "MANAGER", "EMPLOYEE"] as const
export const roleSchema = z.enum(ROLES)
export type OrgRole = z.infer<typeof roleSchema>

export const orgStatusSchema = z.enum(["ACTIVE", "SUSPENDED"])

export const myOrganizationSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  role: roleSchema,
  status: orgStatusSchema,
})
export type MyOrganization = z.infer<typeof myOrganizationSchema>

export const organizationDetailSchema = myOrganizationSchema.extend({
  // Plain strings: a newer server may know permissions this client has not heard of yet
  permissions: z.array(z.string()),
})
export type OrganizationDetail = z.infer<typeof organizationDetailSchema>

function hasControlCharacter(value: string): boolean {
  return [...value].some((character) => {
    const code = character.codePointAt(0) ?? 0
    return code < 32 || code === 127
  })
}

export const createOrganizationSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Enter a name for the organization.")
    .max(120, "Name is too long (maximum 120 characters).")
    .refine((value) => !hasControlCharacter(value), "Name must not contain control characters."),
})
export type CreateOrganizationForm = z.infer<typeof createOrganizationSchema>
