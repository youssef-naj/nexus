import { formatWait } from "@/shared/format"
import { ApiError } from "./client"

/** Wording for failures every feature handles the same way; null means "not one of these". */
export function commonErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return "The request failed. Check your connection and try again."
  }
  if (error.status === 403) return "You don't have permission to do that."
  if (error.status === 429) {
    return error.retryAfterSeconds !== undefined
      ? `Too many attempts. Try again in ${formatWait(error.retryAfterSeconds)}.`
      : "Too many attempts. Please try again later."
  }
  if (error.status >= 500) return "Something went wrong on our side. Please try again."
  return null
}
