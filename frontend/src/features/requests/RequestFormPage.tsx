import { useMemo, useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Link, useNavigate, useParams } from "react-router"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button, buttonVariants } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { useAllDepartments } from "@/features/departments/queries"
import { useOrg } from "@/features/organizations/orgContext"
import { ApiError } from "@/shared/api/client"
import { NativeSelect } from "@/shared/components/NativeSelect"
import { TextAreaField } from "@/shared/components/TextAreaField"
import { TextField } from "@/shared/components/TextField"
import { Label } from "@/components/ui/label"
import { applyServerErrors } from "@/shared/forms/serverErrors"
import { categoryLabel } from "./labels"
import { requestErrorMessage } from "./messages"
import { useCreateRequest, useRequest, useUpdateRequest } from "./queries"
import {
  CATEGORIES,
  categorySchema,
  requestFormSchema,
  type RequestDetail,
  type RequestFormValues,
} from "./schemas"

const EMPTY: RequestFormValues = {
  title: "",
  description: "",
  category: "",
  dueDate: "",
  departmentId: "",
}

function toFormValues(request: RequestDetail): RequestFormValues {
  return {
    title: request.title,
    description: request.description ?? "",
    category: request.category,
    dueDate: request.dueDate ?? "",
    departmentId: request.departmentId ?? "",
  }
}

export function RequestFormPage() {
  const { requestId } = useParams()
  const editing = Boolean(requestId)
  const { organization } = useOrg()
  const orgId = organization.id
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string | null>(null)

  const existing = useRequest(orgId, requestId ?? "")
  const departments = useAllDepartments(orgId)
  const create = useCreateRequest(orgId)
  const update = useUpdateRequest(orgId)
  const request = existing.data
  const initial = useMemo(() => (request ? toFormValues(request) : undefined), [request])

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RequestFormValues>({
    resolver: zodResolver(requestFormSchema),
    defaultValues: EMPTY,
    ...(initial ? { values: initial } : {}),
  })

  const listPath = `/orgs/${orgId}/requests`

  if (editing && existing.isPending) {
    return <Skeleton className="h-32 w-full" aria-label="Loading request" />
  }
  if (editing && existing.error instanceof ApiError && existing.error.status === 404) {
    return (
      <Alert>
        <AlertTitle>Request not found</AlertTitle>
        <AlertDescription>
          <Link className="underline" to={listPath}>
            Back to requests
          </Link>
        </AlertDescription>
      </Alert>
    )
  }
  if (editing && (existing.error || !request)) {
    return (
      <Alert variant="destructive">
        <AlertDescription>
          <p>Couldn't load the request.</p>
          <Button className="mt-3" variant="outline" onClick={() => void existing.refetch()}>
            Try again
          </Button>
        </AlertDescription>
      </Alert>
    )
  }
  if (request && !request.editable) {
    return (
      <Alert>
        <AlertTitle>This request can't be edited</AlertTitle>
        <AlertDescription>
          Only its creator can edit it, and only while it is a draft or has been sent back.{" "}
          <Link className="underline" to={`${listPath}/${request.id}`}>
            Back to the request
          </Link>
        </AlertDescription>
      </Alert>
    )
  }

  // Active departments, plus the request's current one even if it has been deactivated since
  const options = (departments.data?.content ?? []).filter(
    (department) => department.active || department.id === request?.departmentId,
  )

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    const payload = {
      title: values.title,
      description: values.description,
      category: categorySchema.parse(values.category),
      dueDate: values.dueDate || null,
      departmentId: values.departmentId || null,
    }
    try {
      const saved =
        request !== undefined
          ? await update.mutateAsync({ requestId: request.id, payload, version: request.version })
          : await create.mutateAsync(payload)
      navigate(`${listPath}/${saved.id}`)
    } catch (error) {
      if (error instanceof ApiError && error.code === "DEPARTMENT_INACTIVE") {
        setError("departmentId", { message: "This department is inactive. Choose another." })
      } else if (
        error instanceof ApiError &&
        error.status === 404 &&
        !editing &&
        payload.departmentId
      ) {
        setError("departmentId", { message: "This department no longer exists." })
      } else if (
        !applyServerErrors(error, setError, ["title", "description", "category", "dueDate"])
      ) {
        setFormError(requestErrorMessage(error))
      }
    }
  })

  return (
    <section className="grid max-w-2xl gap-6">
      <h1 className="text-2xl font-semibold">{editing ? "Edit request" : "New request"}</h1>
      <form onSubmit={submit} noValidate className="grid gap-4">
        {formError && (
          <Alert variant="destructive">
            <AlertDescription>{formError}</AlertDescription>
          </Alert>
        )}
        <TextField id="request-title" label="Title" error={errors.title} {...register("title")} />
        <TextAreaField
          id="request-description"
          label="Description"
          error={errors.description}
          {...register("description")}
        />
        <div className="grid gap-2">
          <Label htmlFor="request-category">Category</Label>
          <NativeSelect id="request-category" {...register("category")}>
            <option value="">Choose a category…</option>
            {CATEGORIES.map((value) => (
              <option key={value} value={value}>
                {categoryLabel(value)}
              </option>
            ))}
          </NativeSelect>
          {errors.category && (
            <p role="alert" className="text-sm text-destructive">
              {errors.category.message}
            </p>
          )}
        </div>
        <div className="grid gap-2">
          <Label htmlFor="request-department">Department</Label>
          <NativeSelect id="request-department" {...register("departmentId")}>
            <option value="">No department</option>
            {options.map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
                {department.active ? "" : " (inactive)"}
              </option>
            ))}
          </NativeSelect>
          {errors.departmentId && (
            <p role="alert" className="text-sm text-destructive">
              {errors.departmentId.message}
            </p>
          )}
        </div>
        <TextField
          id="request-due"
          label="Due date"
          type="date"
          error={errors.dueDate}
          {...register("dueDate")}
        />
        <div className="flex gap-3">
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Saving…" : editing ? "Save changes" : "Create request"}
          </Button>
          <Link className={buttonVariants({ variant: "ghost" })} to={listPath}>
            Cancel
          </Link>
        </div>
      </form>
    </section>
  )
}
