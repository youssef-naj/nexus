export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  /** Machine-readable reason, for example EMAIL_NOT_VERIFIED */
  code?: string
  errors?: Record<string, string[]>
}

export class ApiError extends Error {
  readonly problem: ProblemDetail
  readonly retryAfterSeconds: number | undefined

  constructor(problem: ProblemDetail, retryAfterSeconds?: number) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`)
    this.name = "ApiError"
    this.problem = problem
    this.retryAfterSeconds = retryAfterSeconds
  }

  get status(): number {
    return this.problem.status
  }

  get code(): string | undefined {
    return this.problem.code
  }
}

const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"])

function readCookie(name: string): string | undefined {
  const entry = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : undefined
}

/** The server issues the CSRF cookie on any response, so one cheap public GET obtains it. */
async function ensureCsrfToken(): Promise<string | undefined> {
  let token = readCookie("XSRF-TOKEN")
  if (!token) {
    await fetch("/api/system/ping", { credentials: "same-origin" }).catch(() => undefined)
    token = readCookie("XSRF-TOKEN")
  }
  return token
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail
  try {
    problem = { ...((await response.json()) as Partial<ProblemDetail>), status: response.status }
  } catch {
    problem = { status: response.status, title: response.statusText }
  }
  const header = response.headers.get("Retry-After")
  const seconds = header === null ? Number.NaN : Number(header)
  return new ApiError(problem, Number.isFinite(seconds) ? seconds : undefined)
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase()
  const headers = new Headers(init.headers)
  headers.set("Accept", "application/json")
  // Strings are sent as JSON; URLSearchParams and FormData set their own content type.
  if (typeof init.body === "string" && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }
  if (UNSAFE_METHODS.has(method)) {
    const token = await ensureCsrfToken()
    if (token) headers.set("X-XSRF-TOKEN", token)
  }

  const response = await fetch(`/api${path}`, {
    ...init,
    method,
    headers,
    credentials: "same-origin",
  })

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
