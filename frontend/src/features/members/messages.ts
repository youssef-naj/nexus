import { formatWait } from "@/features/auth/messages"
import { ApiError } from "@/shared/api/client"

/** Turns the server's machine-readable reasons into sentences a person can act on. */
export function memberErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "STALE_VERSION":
      case "CONCURRENT_MODIFICATION":
        return "Someone else changed this member. The list was refreshed, so please try again."
      case "LAST_OWNER":
        return "An organization must keep at least one owner. Make someone else an owner first."
      case "CANNOT_CHANGE_OWN_ROLE":
        return "You can't change your own role."
      case "CANNOT_REMOVE_SELF":
        return "You can't remove yourself. Use Leave organization instead."
      case "ALREADY_MEMBER":
        return "This person is already a member."
    }
    if (error.status === 403) return "You don't have permission to do that."
    if (error.status === 404) return "That member or invitation no longer exists."
    if (error.status === 429) {
      return error.retryAfterSeconds !== undefined
        ? `Too many attempts. Try again in ${formatWait(error.retryAfterSeconds)}.`
        : "Too many attempts. Please try again later."
    }
    if (error.status >= 500) return "Something went wrong on our side. Please try again."
  }
  return "The request failed. Check your connection and try again."
}
