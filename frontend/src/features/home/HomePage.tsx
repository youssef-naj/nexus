import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { BackendStatus } from "@/features/system/BackendStatus"

export function HomePage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl items-center p-6">
      <Card className="w-full">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-2xl">Nexus</CardTitle>
            <Badge variant="secondary">Foundation</Badge>
          </div>
          <CardDescription>Multi-tenant business operations platform.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          <BackendStatus />
          <div className="flex gap-3">
            <Button>Primary action</Button>
            <Button variant="outline">Secondary</Button>
          </div>
        </CardContent>
      </Card>
    </main>
  )
}
