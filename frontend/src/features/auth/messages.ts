import { ApiError } from "@/shared/api/client"

export function formatWait(seconds: number): string {
  if (seconds < 90) return `${Math.max(1, Math.round(seconds))} seconds`
  return `${Math.ceil(seconds / 60)} minutes`
}

function rateLimited(error: ApiError): string {
  return error.retryAfterSeconds !== undefined
    ? `Too many attempts. Try again in ${formatWait(error.retryAfterSeconds)}.`
    : "Too many attempts. Please try again later."
}

export function loginErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) return rateLimited(error)
    if (error.code === "EMAIL_NOT_VERIFIED") {
      return "Your email address isn't verified yet. Check your inbox for the verification link, or register again to receive a new one."
    }
    if (error.status === 401) return "Invalid email or password."
    if (error.status >= 500) return "Something went wrong on our side. Please try again."
  }
  return "Could not sign in. Check your connection and try again."
}

export function genericErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 429) return rateLimited(error)
    if (error.status >= 500) return "Something went wrong on our side. Please try again."
  }
  return "The request failed. Check your connection and try again."
}
