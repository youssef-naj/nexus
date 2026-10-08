import { ApiError } from "@/shared/api/client"
import { commonErrorMessage } from "@/shared/api/messages"

export function requestErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "STALE_VERSION":
      case "CONCURRENT_MODIFICATION":
        return "Someone else changed this request. It was reloaded, so please review it and try again."
      case "REQUEST_NOT_EDITABLE":
        return "This request can no longer be edited."
      case "NOT_REQUEST_OWNER":
        return "Only the person who created a request can edit or submit it."
      case "DEPARTMENT_INACTIVE":
        return "That department is inactive. Choose another."
      case "INVALID_TRANSITION":
        return "That action is no longer possible: the request was already handled. It was reloaded."
      case "SELF_REVIEW_NOT_ALLOWED":
        return "You can't review a request you created."
    }
    if (error.status === 404) return "That request or department no longer exists."
  }
  return commonErrorMessage(error) ?? "The request failed. Check your connection and try again."
}
