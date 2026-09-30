import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Skeleton } from "@/components/ui/skeleton"
import { usePing } from "./usePing"

export function BackendStatus() {
  const { data, error, isPending } = usePing()

  if (isPending) {
    return <Skeleton className="h-6 w-48" aria-label="Checking backend" />
  }

  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Backend unreachable</AlertTitle>
        <AlertDescription>{error.message}</AlertDescription>
      </Alert>
    )
  }

  return (
    <div className="flex items-center gap-2">
      <Badge>Backend {data.status}</Badge>
      <span className="text-sm text-muted-foreground">
        Server time: {new Date(data.serverTime).toLocaleString()}
      </span>
    </div>
  )
}
