import { Skeleton } from "@/components/ui/skeleton"
import { formatDateTime } from "@/shared/format"
import { actionPastTense, statusLabel } from "./labels"
import { useRequestEvents } from "./queries"

export function RequestHistory({ orgId, requestId }: { orgId: string; requestId: string }) {
  const events = useRequestEvents(orgId, requestId)

  return (
    <section aria-labelledby="history-heading" className="grid gap-3">
      <h2 id="history-heading" className="text-lg font-semibold">
        History
      </h2>
      {events.isPending && <Skeleton className="h-12 w-full" aria-label="Loading history" />}
      {events.isError && <p className="text-sm text-destructive">Couldn't load the history.</p>}
      {events.data?.length === 0 && (
        <p className="text-sm text-muted-foreground">
          No history yet. It starts when the request is submitted.
        </p>
      )}
      {events.data && events.data.length > 0 && (
        <ol className="grid gap-4 border-l pl-4">
          {events.data.map((event) => (
            <li key={event.id}>
              <p className="font-medium">{`${event.actorName} ${actionPastTense(event.action, event.targetName)}`}</p>
              <p className="text-xs text-muted-foreground">
                <time dateTime={event.occurredAt}>{formatDateTime(event.occurredAt)}</time>
                {` · ${statusLabel(event.fromStatus)} → ${statusLabel(event.toStatus)}`}
              </p>
              {event.comment && <p className="mt-1 whitespace-pre-wrap text-sm">{event.comment}</p>}
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
