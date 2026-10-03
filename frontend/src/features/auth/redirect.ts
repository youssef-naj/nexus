export function safeRedirect(state: unknown): string {
  if (typeof state === "object" && state !== null && "from" in state) {
    const from = (state as { from?: unknown }).from
    if (typeof from === "string" && from.startsWith("/") && !from.startsWith("//")) {
      return from
    }
  }
  return "/"
}
