import { useState } from "react"
import { Alert, AlertDescription } from "@/components/ui/alert"
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
import {
  EVENT_LABELS,
  PLATFORM_EVENT_TYPES,
  describeMetadata,
  eventLabel,
} from "@/features/audit/labels"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { PaginationBar } from "@/shared/components/PaginationBar"
import { ScrollRegion } from "@/shared/components/ScrollRegion"
import { formatDateTime } from "@/shared/format"
import { usePlatformAudit } from "./queries"

export function PlatformAuditPage() {
  const [page, setPage] = useState(0)
  const [eventType, setEventType] = useState("")
  const audit = usePlatformAudit({ page, eventType: eventType || undefined })

  return (
    <section className="grid gap-6">
      <div>
        <h1 className="text-2xl font-semibold">Platform audit</h1>
        <p className="text-sm text-muted-foreground">
          Actions taken on the platform itself, newest first. What people do inside an organization
          is not shown here.
        </p>
      </div>

      <div className="grid max-w-xs gap-2">
        <Label htmlFor="platform-audit-event">Event type</Label>
        <NativeSelect
          id="platform-audit-event"
          value={eventType}
          onChange={(event) => {
            setEventType(event.target.value)
            setPage(0)
          }}
        >
          <option value="">All events</option>
          {PLATFORM_EVENT_TYPES.map((type) => (
            <option key={type} value={type}>
              {EVENT_LABELS[type]}
            </option>
          ))}
        </NativeSelect>
      </div>

      {audit.isPending && <Skeleton className="h-32 w-full" aria-label="Loading platform audit" />}

      {audit.isError && (
        <Alert variant="destructive">
          <AlertDescription>
            <p>Couldn't load the platform audit.</p>
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
              {eventType ? "No events match this filter." : "No platform events yet."}
            </p>
          ) : (
            <ScrollRegion label="Platform audit events">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>When</TableHead>
                    <TableHead>Event</TableHead>
                    <TableHead>By</TableHead>
                    <TableHead>Concerns</TableHead>
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
                      <TableCell>{entry.targetName ?? "—"}</TableCell>
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
