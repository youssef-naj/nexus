import { useState } from "react"
import { Link, useParams } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
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
import { roleLabel } from "@/features/organizations/labels"
import { useOrg } from "@/features/organizations/orgContext"
import { ApiError } from "@/shared/api/client"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { PaginationBar } from "@/shared/components/PaginationBar"
import { formatDate } from "@/shared/format"
import { DepartmentForm } from "./DepartmentForm"
import { departmentErrorMessage } from "./messages"
import {
  useAssignableMembers,
  useAssignMember,
  useDepartment,
  useDepartmentMembers,
  useSetDepartmentActive,
  useUnassignMember,
  useUpdateDepartment,
} from "./queries"
import { ScrollRegion } from "@/shared/components/ScrollRegion"

export function DepartmentDetailPage() {
  const { departmentId = "" } = useParams()
  const { organization, can } = useOrg()
  const orgId = organization.id
  const manage = can("DEPARTMENT_MANAGE")
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState("")
  const [message, setMessage] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [confirmingDeactivate, setConfirmingDeactivate] = useState(false)

  const department = useDepartment(orgId, departmentId)
  const members = useDepartmentMembers(orgId, departmentId, page)
  const candidates = useAssignableMembers(orgId, manage)
  const update = useUpdateDepartment(orgId)
  const setActive = useSetDepartmentActive(orgId)
  const assign = useAssignMember(orgId, departmentId)
  const unassign = useUnassignMember(orgId, departmentId)

  if (department.isPending) {
    return <Skeleton className="h-24 w-full" aria-label="Loading department" />
  }
  if (department.error instanceof ApiError && department.error.status === 404) {
    return (
      <Alert>
        <AlertTitle>Department not found</AlertTitle>
        <AlertDescription>
          <Link className="underline" to="..">
            Back to departments
          </Link>
        </AlertDescription>
      </Alert>
    )
  }
  if (department.error || !department.data) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          <p>Couldn't load the department.</p>
          <Button className="mt-3" variant="outline" onClick={() => void department.refetch()}>
            Try again
          </Button>
        </AlertDescription>
      </Alert>
    )
  }
  const current = department.data

  async function run(action: () => Promise<unknown>, success?: string) {
    setMessage(null)
    setNotice(null)
    try {
      await action()
      if (success) setNotice(success)
    } catch (error) {
      setMessage(departmentErrorMessage(error))
    }
  }

  async function onAdd() {
    const candidate = candidates.data?.content.find((member) => member.id === selected)
    if (!candidate) return
    await run(() => assign.mutateAsync(candidate.id), `Added ${candidate.displayName}.`)
    setSelected("")
  }

  return (
    <section className="grid gap-6">
      <div>
        <Link className="text-sm underline" to="..">
          Back to departments
        </Link>
        <div className="mt-2 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold">{current.name}</h1>
          {!current.active && <Badge variant="secondary">Inactive</Badge>}
        </div>
        {!manage && current.description && (
          <p className="mt-2 whitespace-pre-wrap text-muted-foreground">{current.description}</p>
        )}
      </div>

      {message && (
        <Alert variant="destructive">
          <AlertDescription>{message}</AlertDescription>
        </Alert>
      )}
      {notice && (
        <Alert role="status">
          <AlertDescription>{notice}</AlertDescription>
        </Alert>
      )}

      {manage && (
        <section aria-labelledby="edit-department-heading" className="grid max-w-md gap-3">
          <h2 id="edit-department-heading" className="text-lg font-semibold">
            Edit department
          </h2>
          <DepartmentForm
            initial={{ name: current.name, description: current.description ?? "" }}
            submitLabel="Save changes"
            pendingLabel="Saving…"
            onSubmit={async (values) => {
              setNotice(null)
              await update.mutateAsync({ id: current.id, values, version: current.version })
              setNotice("Changes saved.")
            }}
          />
          <div className="flex items-center gap-3">
            {current.active ? (
              confirmingDeactivate ? (
                <>
                  <Button
                    variant="destructive"
                    size="sm"
                    disabled={setActive.isPending}
                    onClick={() => {
                      setConfirmingDeactivate(false)
                      void run(() => setActive.mutateAsync({ id: current.id, active: false }))
                    }}
                  >
                    Confirm deactivation
                  </Button>
                  <Button variant="ghost" size="sm" onClick={() => setConfirmingDeactivate(false)}>
                    Cancel
                  </Button>
                </>
              ) : (
                <Button variant="outline" size="sm" onClick={() => setConfirmingDeactivate(true)}>
                  Deactivate department
                </Button>
              )
            ) : (
              <Button
                variant="outline"
                size="sm"
                disabled={setActive.isPending}
                onClick={() =>
                  void run(() => setActive.mutateAsync({ id: current.id, active: true }))
                }
              >
                Reactivate department
              </Button>
            )}
          </div>
        </section>
      )}

      <section aria-labelledby="department-members-heading" className="grid gap-3">
        <h2 id="department-members-heading" className="text-lg font-semibold">
          Members
        </h2>
        {members.isPending && <Skeleton className="h-16 w-full" aria-label="Loading members" />}
        {members.isError && <p className="text-sm text-destructive">Couldn't load the members.</p>}
        {members.data?.content.length === 0 && (
          <p className="text-sm text-muted-foreground">No members in this department yet.</p>
        )}
        {members.data && members.data.content.length > 0 && (
          <ScrollRegion label="Department members">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Name</TableHead>
                  <TableHead>Role</TableHead>
                  <TableHead>Added</TableHead>
                  <TableHead>
                    <span className="sr-only">Actions</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {members.data.content.map((member) => (
                  <TableRow key={member.membershipId}>
                    <TableCell className="font-medium">
                      {member.displayName}
                      {member.email && (
                        <span className="ml-2 text-xs text-muted-foreground">{member.email}</span>
                      )}
                    </TableCell>
                    <TableCell>{roleLabel(member.role)}</TableCell>
                    <TableCell>{formatDate(member.assignedAt)}</TableCell>
                    <TableCell className="text-right">
                      {manage && (
                        <Button
                          variant="outline"
                          size="sm"
                          disabled={unassign.isPending}
                          aria-label={`Remove ${member.displayName} from department`}
                          onClick={() => void run(() => unassign.mutateAsync(member.membershipId))}
                        >
                          Remove
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </ScrollRegion>
        )}
        {members.data && (
          <PaginationBar page={page} totalPages={members.data.totalPages} onPageChange={setPage} />
        )}

        {manage &&
          (current.active ? (
            <div className="flex flex-wrap items-end gap-2">
              <div className="grid gap-2">
                <Label htmlFor="add-member">Add a member</Label>
                <NativeSelect
                  id="add-member"
                  value={selected}
                  onChange={(event) => setSelected(event.target.value)}
                >
                  <option value="">Choose a member…</option>
                  {candidates.data?.content.map((member) => (
                    <option key={member.id} value={member.id}>
                      {member.displayName}
                    </option>
                  ))}
                </NativeSelect>
              </div>
              <Button disabled={!selected || assign.isPending} onClick={() => void onAdd()}>
                Add member
              </Button>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              Inactive departments cannot receive new members.
            </p>
          ))}
      </section>
    </section>
  )
}
