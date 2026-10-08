export type QueryValue = string | number | boolean | null | undefined

/** Builds "?a=1&b=2", skipping empty values and URL-encoding every value. */
export function buildQuery(params: Record<string, QueryValue>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === "") continue
    search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ""
}
