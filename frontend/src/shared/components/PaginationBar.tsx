import { Button } from "@/components/ui/button"

export function PaginationBar({
  page,
  totalPages,
  onPageChange,
}: {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}) {
  const pages = Math.max(totalPages, 1)
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between">
      <span className="text-sm text-muted-foreground">
        Page {page + 1} of {pages}
      </span>
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          disabled={page === 0}
          onClick={() => onPageChange(page - 1)}
        >
          Previous page
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={page + 1 >= pages}
          onClick={() => onPageChange(page + 1)}
        >
          Next page
        </Button>
      </div>
    </nav>
  )
}
