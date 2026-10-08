import { useState } from "react"
import { Link } from "react-router"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Skeleton } from "@/components/ui/skeleton"
import { Can } from "@/features/organizations/Can"
import { useOrg } from "@/features/organizations/orgContext"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { PaginationBar } from "@/shared/components/PaginationBar"
import { DepartmentForm } from "./DepartmentForm"
import { useCreateDepartment, useDepartments } from "./queries"

type StateFilter = "active" | "inactive" | "all"

function parseState(value: string): StateFilter {
  return value === "inactive" || value === "all" ? value : "active"
}

export function DepartmentsPage() {
  const { organization } = useOrg()
  const orgId = organization.id
  const [page, setPage] = useState(0)
  const [state, setState] = useState<StateFilter>("active")
  const [searchInput, setSearchInput] = useState("")
  const [query, setQuery] = useState("")
  const [notice, setNotice] = useState<string | null>(null)

  const departments = useDepartments(orgId, {
    page,
    active: state === "all" ? undefined : state === "active",
    q: query || undefined,
  })
  const create = useCreateDepartment(orgId)

  return (
    <section className="grid gap-6">
      <h1 className="text-2xl font-semibold">Departments</h1>

      <div className="flex flex-wrap items-end gap-4">
        <div className="grid gap-2">
          <Label htmlFor="department-state">Show</Label>
          <NativeSelect
            id="department-state"
            value={state}
            onChange={(event) => {
              setState(parseState(event.target.value))
              setPage(0)
            }}
          >
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
            <option value="all">All</option>
          </NativeSelect>
        </div>
        <form
          className="flex items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            setQuery(searchInput.trim())
            setPage(0)
          }}
        >
          <div className="grid gap-2">
            <Label htmlFor="department-search">Search departments</Label>
            <Input
              id="department-search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
          </div>
          <Button type="submit" variant="outline">
            Search
          </Button>
        </form>
      </div>

      {departments.isPending && (
        <Skeleton className="h-24 w-full" aria-label="Loading departments" />
      )}

      {departments.isError && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>Couldn't load the departments.</p>
            <Button className="mt-3" variant="outline" onClick={() => void departments.refetch()}>
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {departments.data && (
        <>
          {departments.data.content.length === 0 ? (
            <p className="text-sm text-muted-foreground">No departments found.</p>
          ) : (
            <ul className="grid gap-3">
              {departments.data.content.map((department) => (
                <li key={department.id} className="rounded-lg border p-4">
                  <div className="flex items-center justify-between gap-2">
                    <Link
                      className="font-medium underline-offset-4 hover:underline"
                      to={department.id}
                    >
                      {department.name}
                    </Link>
                    {!department.active && <Badge variant="secondary">Inactive</Badge>}
                  </div>
                  {department.description && (
                    <p className="mt-1 whitespace-pre-wrap text-sm text-muted-foreground">
                      {department.description}
                    </p>
                  )}
                </li>
              ))}
            </ul>
          )}
          <PaginationBar
            page={page}
            totalPages={departments.data.totalPages}
            onPageChange={setPage}
          />
        </>
      )}

      <Can permission="DEPARTMENT_MANAGE">
        <section
          aria-labelledby="new-department-heading"
          className="grid max-w-md gap-3 border-t pt-6"
        >
          <h2 id="new-department-heading" className="text-lg font-semibold">
            New department
          </h2>
          {notice && (
            <Alert role="status">
              <AlertDescription>{notice}</AlertDescription>
            </Alert>
          )}
          <DepartmentForm
            resetOnSuccess
            submitLabel="Create department"
            pendingLabel="Creating…"
            onSubmit={async (values) => {
              setNotice(null)
              await create.mutateAsync(values)
              setNotice("Department created.")
            }}
          />
        </section>
      </Can>
    </section>
  )
}
