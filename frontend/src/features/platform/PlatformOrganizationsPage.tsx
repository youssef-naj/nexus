import { useState } from "react"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
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
import { reasonLabel } from "@/features/audit/labels"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { PaginationBar } from "@/shared/components/PaginationBar"
import { ScrollRegion } from "@/shared/components/ScrollRegion"
import { formatDate } from "@/shared/format"
import { platformErrorMessage } from "./messages"
import { useChangeOrganizationStatus, usePlatformOrganizations } from "./queries"
import {
  REACTIVATE_REASONS,
  SUSPEND_REASONS,
  type PlatformOrganization,
  type StatusAction,
} from "./schemas"

type StateFilter = "" | "ACTIVE" | "SUSPENDED"

function parseState(value: string): StateFilter {
  return value === "ACTIVE" || value === "SUSPENDED" ? value : ""
}

export function PlatformOrganizationsPage() {
  const [page, setPage] = useState(0)
  const [state, setState] = useState<StateFilter>("")
  const [searchInput, setSearchInput] = useState("")
  const [query, setQuery] = useState("")
  const [message, setMessage] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const organizations = usePlatformOrganizations({
    page,
    status: state || undefined,
    q: query || undefined,
  })
  const change = useChangeOrganizationStatus()

  async function apply(organization: PlatformOrganization, action: StatusAction, reason: string) {
    setMessage(null)
    setNotice(null)
    try {
      await change.mutateAsync({
        organizationId: organization.id,
        action,
        reason,
        version: organization.version,
      })
      setNotice(`${organization.name} ${action === "suspend" ? "suspended" : "reactivated"}.`)
    } catch (error) {
      setMessage(platformErrorMessage(error))
    }
  }

  return (
    <section className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Organizations</h1>
        <p className="text-sm text-muted-foreground">
          Metadata only: you can see and change an organization's status, never its members' data.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="grid gap-2">
          <Label htmlFor="platform-status">Status</Label>
          <NativeSelect
            id="platform-status"
            value={state}
            onChange={(event) => {
              setState(parseState(event.target.value))
              setPage(0)
            }}
          >
            <option value="">All</option>
            <option value="ACTIVE">Active</option>
            <option value="SUSPENDED">Suspended</option>
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
            <Label htmlFor="platform-search">Search organizations</Label>
            <Input
              id="platform-search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
          </div>
          <Button type="submit" variant="outline">
            Search
          </Button>
        </form>
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

      {organizations.isPending && (
        <Skeleton className="h-32 w-full" aria-label="Loading organizations" />
      )}

      {organizations.isError && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>Couldn't load the organizations.</p>
            <Button className="mt-3" variant="outline" onClick={() => void organizations.refetch()}>
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {organizations.data && (
        <>
          {organizations.data.content.length === 0 ? (
            <p className="text-sm text-muted-foreground">No organizations match.</p>
          ) : (
            <ScrollRegion label="Organizations">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>Slug</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Members</TableHead>
                    <TableHead>Created</TableHead>
                    <TableHead>
                      <span className="sr-only">Actions</span>
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {organizations.data.content.map((organization) => (
                    <OrganizationRow
                      key={organization.id}
                      organization={organization}
                      busy={change.isPending}
                      onApply={(action, reason) => void apply(organization, action, reason)}
                    />
                  ))}
                </TableBody>
              </Table>
            </ScrollRegion>
          )}
          <PaginationBar
            page={page}
            totalPages={organizations.data.totalPages}
            onPageChange={setPage}
          />
        </>
      )}
    </section>
  )
}

function OrganizationRow({
  organization,
  busy,
  onApply,
}: {
  organization: PlatformOrganization
  busy: boolean
  onApply: (action: StatusAction, reason: string) => void
}) {
  const [acting, setActing] = useState(false)
  const [reason, setReason] = useState("")
  const suspended = organization.status === "SUSPENDED"
  const action: StatusAction = suspended ? "reactivate" : "suspend"
  const reasons = suspended ? REACTIVATE_REASONS : SUSPEND_REASONS

  return (
    <TableRow>
      <TableCell className="font-medium">{organization.name}</TableCell>
      <TableCell>{organization.slug}</TableCell>
      <TableCell>
        <Badge variant={suspended ? "destructive" : "secondary"}>
          {suspended ? "Suspended" : "Active"}
        </Badge>
      </TableCell>
      <TableCell>{organization.memberCount}</TableCell>
      <TableCell>{formatDate(organization.createdAt)}</TableCell>
      <TableCell className="text-right">
        {acting ? (
          <span className="inline-flex flex-wrap items-center justify-end gap-2">
            <NativeSelect
              aria-label={`Reason for ${suspended ? "reactivating" : "suspending"} ${organization.name}`}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
            >
              <option value="">Choose a reason…</option>
              {reasons.map((code) => (
                <option key={code} value={code}>
                  {reasonLabel(code)}
                </option>
              ))}
            </NativeSelect>
            <Button
              variant={suspended ? "default" : "destructive"}
              size="sm"
              disabled={!reason || busy}
              aria-label={`Confirm ${suspended ? "reactivation" : "suspension"} of ${organization.name}`}
              onClick={() => {
                setActing(false)
                onApply(action, reason)
                setReason("")
              }}
            >
              Confirm
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setActing(false)}>
              Cancel
            </Button>
          </span>
        ) : (
          <Button
            variant="outline"
            size="sm"
            aria-label={`${suspended ? "Reactivate" : "Suspend"} ${organization.name}`}
            onClick={() => setActing(true)}
          >
            {suspended ? "Reactivate" : "Suspend"}
          </Button>
        )}
      </TableCell>
    </TableRow>
  )
}
