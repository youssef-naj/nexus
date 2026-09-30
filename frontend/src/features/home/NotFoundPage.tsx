import { Link } from "react-router"

export function NotFoundPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col items-center justify-center gap-2 p-6">
      <h1 className="text-2xl font-semibold">Page not found</h1>
      <Link className="underline" to="/">
        Back to home
      </Link>
    </main>
  )
}
