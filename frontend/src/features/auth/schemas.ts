import { z } from "zod"

const MAX_PASSWORD_BYTES = 72 // bcrypt's input limit, mirrored from the backend policy

export const loginSchema = z.object({
  email: z.email("Enter a valid email address."),
  password: z.string().min(1, "Enter your password."),
})
export type LoginForm = z.infer<typeof loginSchema>

export const registerSchema = z.object({
  displayName: z.string().trim().min(1, "Enter your name.").max(120, "Name is too long."),
  email: z.email("Enter a valid email address.").max(320, "Email is too long."),
  password: z
    .string()
    .min(12, "Password must be at least 12 characters long.")
    .refine(
      (value) => new TextEncoder().encode(value).length <= MAX_PASSWORD_BYTES,
      "Password is too long (maximum 72 bytes).",
    ),
})
export type RegisterForm = z.infer<typeof registerSchema>
