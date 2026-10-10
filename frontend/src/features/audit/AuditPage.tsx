import { useState } from "react"
import { Alert, AlertDescription } from "@/components/ui/alert"
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
import { useOrg } from "@/features/organizations/orgContext"
import { ApiError } from "@/shared/api/client"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { PaginationBar } from "@/shared/components/PaginationBar"
import { formatDateTime } from "@/shared/format"
import { EVENT_LABELS, EVENT_TYPES, describeMetadata, eventLabel } from "./labels"
import { useAudit } from "./queries"
import { ScrollRegion } from "@/shared/components/ScrollRegion"

export function AuditPage() {
  const { organization } = useOrg()
  const [page, setPage] = useState(0)
  const [eventType, setEventType] = useState("")
  const [from, setFrom] = useState("")
  const [to, setTo] = useState("")

  const audit = useAudit(organization.id, {
    page,
    eventType: eventType || undefined,
    from: from || undefined,
    to: to || undefined,
  })
  const filtering = Boolean(eventType || from || to)

  function changed(setter: (value: string) => void) {
    return (value: string) => {
      setter(value)
      setPage(0)
    }
  }

  return (
    <section className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Audit log</h1>
        <p className="text-sm text-muted-foreground">
          Security and business events of this organization, newest first.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-4">
        <div className="grid gap-2">
          <Label htmlFor="audit-event">Event type</Label>
          <NativeSelect
            id="audit-event"
            value={eventType}
            onChange={(event) => changed(setEventType)(event.target.value)}
          >
            <option value="">All events</option>
            {EVENT_TYPES.map((type) => (
              <option key={type} value={type}>
                {EVENT_LABELS[type]}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="audit-from">From date</Label>
          <Input
            id="audit-from"
            type="date"
            value={from}
            onChange={(event) => changed(setFrom)(event.target.value)}
          />
        </div>
        <div className="grid gap-2">
          <Label htmlFor="audit-to">To date</Label>
          <Input
            id="audit-to"
            type="date"
            value={to}
            onChange={(event) => changed(setTo)(event.target.value)}
          />
        </div>
      </div>

      {audit.isPending && <Skeleton className="h-32 w-full" aria-label="Loading audit log" />}

      {audit.error instanceof ApiError && audit.error.status === 403 && (
        <Alert>
          <AlertDescription>You don't have permission to view the audit log.</AlertDescription>
        </Alert>
      )}

      {audit.isError && !(audit.error instanceof ApiError && audit.error.status === 403) && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>Couldn't load the audit log.</p>
            <Button className="mt-3" variant="outline" onClick={() => void audit.refetch()}>
              Try again
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {audit.data && (
        <>
          {audit.data.content.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              {filtering ? "No events match these filters." : "No audit events yet."}
            </p>
          ) : (
            <ScrollRegion label="Audit events">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>When</TableHead>
                    <TableHead>Event</TableHead>
                    <TableHead>By</TableHead>
                    <TableHead>Details</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {audit.data.content.map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell className="whitespace-nowrap">
                        {formatDateTime(entry.occurredAt)}
                      </TableCell>
                      <TableCell className="font-medium">{eventLabel(entry.eventType)}</TableCell>
                      <TableCell>{entry.actorName ?? "System"}</TableCell>
                      <TableCell>{describeMetadata(entry.metadata)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </ScrollRegion>
          )}
          <PaginationBar page={page} totalPages={audit.data.totalPages} onPageChange={setPage} />
        </>
      )}
    </section>
  )
}
