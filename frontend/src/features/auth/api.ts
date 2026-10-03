import { z } from "zod"
import { ApiError, apiFetch } from "@/shared/api/client"
import type { LoginForm, RegisterForm } from "./schemas"

const currentUserSchema = z.object({
  id: z.string(),
  email: z.string(),
  displayName: z.string(),
})
export type CurrentUser = z.infer<typeof currentUserSchema>

/** The signed-in user, or null when there is no valid session (401 is data, not an error). */
export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  try {
    return currentUserSchema.parse(await apiFetch<unknown>("/auth/me"))
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

/** Login is a form post (Spring's formLogin), not JSON. */
export async function login(credentials: LoginForm): Promise<void> {
  await apiFetch<void>("/auth/login", {
    method: "POST",
    body: new URLSearchParams({ email: credentials.email, password: credentials.password }),
  })
}

export async function logout(): Promise<void> {
  await apiFetch<void>("/auth/logout", { method: "POST" })
}

export async function register(data: RegisterForm): Promise<void> {
  await apiFetch<unknown>("/auth/register", { method: "POST", body: JSON.stringify(data) })
}

export async function verifyEmail(token: string): Promise<void> {
  await apiFetch<void>("/auth/verify-email", {
    method: "POST",
    body: JSON.stringify({ token }),
  })
}
