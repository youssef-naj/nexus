export function formatWait(seconds: number): string {
  if (seconds < 90) return `${Math.max(1, Math.round(seconds))} seconds`
  return `${Math.ceil(seconds / 60)} minutes`
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString()
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString()
}

/** A calendar date such as "2026-10-12" (no time zone), shown as the same day for everyone. */
export function formatDueDate(date: string): string {
  return new Date(`${date}T00:00:00`).toLocaleDateString()
}
