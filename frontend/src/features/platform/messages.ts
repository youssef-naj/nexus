import { ApiError } from "@/shared/api/client"
import { commonErrorMessage } from "@/shared/api/messages"

export function platformErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "STALE_VERSION":
      case "CONCURRENT_MODIFICATION":
        return "This organization changed since you opened it. The list was reloaded, so please try again."
    }
    if (error.problem.errors?.["reason"]) return "Choose a reason that fits this action."
    if (error.status === 404)
      return "That organization no longer exists, or you no longer have access."
  }
  return commonErrorMessage(error) ?? "The request failed. Check your connection and try again."
}
