export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  traceId?: string
  errors?: Record<string, string[]>
}

export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`)
    this.name = "ApiError"
    this.problem = problem
  }

  get status(): number {
    return this.problem.status
  }
}

const UNSAFE_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"])

function readCookie(name: string): string | undefined {
  const entry = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`))
  return entry ? decodeURIComponent(entry.slice(name.length + 1)) : undefined
}

async function toProblem(response: Response): Promise<ProblemDetail> {
  try {
    const body = (await response.json()) as Partial<ProblemDetail>
    return { ...body, status: response.status }
  } catch {
    return { status: response.status, title: response.statusText }
  }
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase()
  const headers = new Headers(init.headers)
  headers.set("Accept", "application/json")
  if (init.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }
  if (UNSAFE_METHODS.has(method)) {
    const token = readCookie("XSRF-TOKEN")
    if (token) headers.set("X-XSRF-TOKEN", token)
  }

  const response = await fetch(`/api${path}`, {
    ...init,
    method,
    headers,
    credentials: "same-origin",
  })

  if (!response.ok) throw new ApiError(await toProblem(response))
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
