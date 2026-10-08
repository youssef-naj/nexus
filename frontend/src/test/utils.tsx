import type { ReactElement } from "react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render } from "@testing-library/react"
import { MemoryRouter, type InitialEntry } from "react-router"
import { vi } from "vitest"

function newClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
}

export function renderWithQuery(ui: ReactElement) {
  return render(<QueryClientProvider client={newClient()}>{ui}</QueryClientProvider>)
}

export function renderWithProviders(ui: ReactElement, initialEntries: InitialEntry[] = ["/"]) {
  return render(
    <QueryClientProvider client={newClient()}>
      <MemoryRouter initialEntries={initialEntries}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}

export function jsonResponse(
  status: number,
  body?: unknown,
  headers: Record<string, string> = {},
): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  })
}

/**
 * Stubs fetch with handlers keyed by "METHOD /path" (without the /api prefix). A key with a query
 * string matches exactly; a key without one matches that path with any query string.
 */
export function mockApi(routes: Record<string, (init?: RequestInit) => Response>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input).replace(/^\/api/, "")
    const method = (init?.method ?? "GET").toUpperCase()
    const handler = routes[`${method} ${url}`] ?? routes[`${method} ${url.split("?")[0]}`]
    if (!handler) throw new Error(`Unexpected request: ${method} ${url}`)
    return handler(init)
  })
  vi.stubGlobal("fetch", fetchMock)
  return fetchMock
}
