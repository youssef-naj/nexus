import type { FieldValues, Path, UseFormSetError } from "react-hook-form"
import { ApiError } from "@/shared/api/client"

/**
 * Copies field errors from a Problem Details response onto the form.
 * Returns true when at least one error was shown on a field.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  knownFields: readonly Path<T>[],
): boolean {
  if (!(error instanceof ApiError) || !error.problem.errors) return false
  let applied = false
  for (const [field, messages] of Object.entries(error.problem.errors)) {
    if ((knownFields as readonly string[]).includes(field)) {
      setError(field as Path<T>, { type: "server", message: messages.join(" ") })
      applied = true
    }
  }
  return applied
}
