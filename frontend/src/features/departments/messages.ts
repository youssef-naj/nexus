import { ApiError } from "@/shared/api/client"
import { commonErrorMessage } from "@/shared/api/messages"

export function departmentErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "DEPARTMENT_NAME_TAKEN":
        return "A department with this name already exists."
      case "STALE_VERSION":
      case "CONCURRENT_MODIFICATION":
        return "Someone else changed this department. It was reloaded, so please try again."
      case "DEPARTMENT_INACTIVE":
        return "This department is inactive and cannot receive new members."
    }
    if (error.status === 404) return "That department or member no longer exists."
  }
  return commonErrorMessage(error) ?? "The request failed. Check your connection and try again."
}
