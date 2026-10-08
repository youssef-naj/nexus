import { useState } from "react"
import { Link } from "react-router"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button, buttonVariants } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { useAllDepartments } from "@/features/departments/queries"
import { useOrg } from "@/features/organizations/orgContext"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { PaginationBar } from "@/shared/components/PaginationBar"
import { formatDate, formatDueDate } from "@/shared/format"
import { categoryLabel } from "./labels"
import { RequestStatusBadge } from "./RequestStatusBadge"
import { useRequests } from "./queries"
import {
  CATEGORIES,
  STATUSES,
  categorySchema,
  statusSchema,
  type RequestCategory,
  type RequestStatus,
} from "./schemas"
import { statusLabel } from "./labels"

const SORTS = {
  newest: { sort: "CREATED", direction: "DESC", label: "Newest first" },
  oldest: { sort: "CREATED", direction: "ASC", label: "Oldest first" },
  updated: { sort: "UPDATED", direction: "DESC", label: "Recently updated" },
  dueSoon: { sort: "DUE_DATE", direction: "ASC", label: "Due soonest" },
  dueLate: { sort: "DUE_DATE", direction: "DESC", label: "Due latest" },
} as const
type SortKey = keyof typeof SORTS

function parseSort(value: string): SortKey {
  return value in SORTS ? (value as SortKey) : "newest"
}

export function RequestsPage() {
  const { organization, can } = useOrg()
  const orgId = organization.id
  const canViewAll = can("REQUEST_VIEW_ALL")
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<RequestStatus | "">("")
  const [category, setCategory] = useState<RequestCategory | "">("")
  const [departmentId, setDepartmentId] = useState("")
  const [mine, setMine] = useState(false)
  const [sortKey, setSortKey] = useState<SortKey>("newest")
  const [searchInput, setSearchInput] = useState("")
  const [query, setQuery] = useState("")

  const departments = useAllDepartments(orgId)
  const requests = useRequests(orgId, {
    page,
    status,
    category,
    departmentId,
    mine: mine || undefined,
    q: query,
    sort: SORTS[sortKey].sort,
    direction: SORTS[sortKey].direction,
  })
  const filtering = Boolean(status || category || departmentId || mine || query)

  function changed<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value)
      setPage(0)
    }
  }

  return (
    <section className="grid gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Requests</h1>
        {can("REQUEST_CREATE") && (
          <Link className={buttonVariants()} to="new">
            New request
          </Link>
        )}
      </div>

      {!canViewAll && (
        <p className="text-sm text-muted-foreground">You see the requests you created.</p>
      )}

      <div className="flex flex-wrap items-end gap-4">
        <div className="grid gap-2">
          <Label htmlFor="filter-status">Status</Label>
          <NativeSelect
            id="filter-status"
            value={status}
            onChange={(event) =>
              changed(setStatus)(statusSchema.safeParse(event.target.value).data ?? "")
            }
          >
            <option value="">All statuses</option>
            {STATUSES.map((value) => (
              <option key={value} value={value}>
                {statusLabel(value)}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="filter-category">Category</Label>
          <NativeSelect
            id="filter-category"
            value={category}
            onChange={(event) =>
              changed(setCategory)(categorySchema.safeParse(event.target.value).data ?? "")
            }
          >
            <option value="">All categories</option>
            {CATEGORIES.map((value) => (
              <option key={value} value={value}>
                {categoryLabel(value)}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="filter-department">Department</Label>
          <NativeSelect
            id="filter-department"
            value={departmentId}
            onChange={(event) => changed(setDepartmentId)(event.target.value)}
          >
            <option value="">All departments</option>
            {departments.data?.content.map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
                {department.active ? "" : " (inactive)"}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="filter-sort">Sort by</Label>
          <NativeSelect
            id="filter-sort"
            value={sortKey}
            onChange={(event) => changed(setSortKey)(parseSort(event.target.value))}
          >
            {Object.entries(SORTS).map(([key, option]) => (
              <option key={key} value={key}>
                {option.label}
              </option>
            ))}
          </NativeSelect>
        </div>
        {canViewAll && (
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={mine}
              onChange={(event) => changed(setMine)(event.target.checked)}
            />
            Only my requests
          </label>
        )}
        <form
          className="flex items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            changed(setQuery)(searchInput.trim())
          }}
        >
          <div className="grid gap-2">
            <Label htmlFor="filter-search">Search requests</Label>
            <Input
              id="filter-search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
          </div>
          <Button type="submit" variant="outline">
            Search
          </Button>
        </form>
      </div>

      {requests.isPending && <Skeleton className="h-32 w-full" aria-label="Loading requests" />}

      {requests.isError && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>Couldn't load the requests.</p>
            <Button className="mt-3" variant="outline" onClick={() => void requests.refetch()}>
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {requests.data && (
        <>
          {requests.data.content.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              {filtering ? "No requests match these filters." : "No requests yet."}
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Reference</TableHead>
                  <TableHead>Title</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead>Status</TableHead>
                  {canViewAll && <TableHead>Created by</TableHead>}
                  <TableHead>Department</TableHead>
                  <TableHead>Due</TableHead>
                  <TableHead>Updated</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {requests.data.content.map((request) => (
                  <TableRow key={request.id}>
                    <TableCell className="whitespace-nowrap">{request.reference}</TableCell>
                    <TableCell className="font-medium">
                      <Link className="underline-offset-4 hover:underline" to={request.id}>
                        {request.title}
                      </Link>
                    </TableCell>
                    <TableCell>{categoryLabel(request.category)}</TableCell>
                    <TableCell>
                      <RequestStatusBadge status={request.status} />
                    </TableCell>
                    {canViewAll && <TableCell>{request.createdByName}</TableCell>}
                    <TableCell>{request.departmentName ?? "—"}</TableCell>
                    <TableCell>{request.dueDate ? formatDueDate(request.dueDate) : "—"}</TableCell>
                    <TableCell>{formatDate(request.updatedAt)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
          <PaginationBar page={page} totalPages={requests.data.totalPages} onPageChange={setPage} />
        </>
      )}
    </section>
  )
}
