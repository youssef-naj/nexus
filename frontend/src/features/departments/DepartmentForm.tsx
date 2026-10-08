import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { ApiError } from "@/shared/api/client"
import { TextAreaField } from "@/shared/components/TextAreaField"
import { TextField } from "@/shared/components/TextField"
import { applyServerErrors } from "@/shared/forms/serverErrors"
import { departmentErrorMessage } from "./messages"
import { departmentFormSchema, type DepartmentFormValues } from "./schemas"

interface DepartmentFormProps {
  /** When given, the form shows (and re-syncs to) these values: the edit case. */
  initial?: DepartmentFormValues | undefined
  submitLabel: string
  pendingLabel: string
  resetOnSuccess?: boolean
  onSubmit: (values: DepartmentFormValues) => Promise<void>
}

export function DepartmentForm({
  initial,
  submitLabel,
  pendingLabel,
  resetOnSuccess = false,
  onSubmit,
}: DepartmentFormProps) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<DepartmentFormValues>({
    resolver: zodResolver(departmentFormSchema),
    defaultValues: { name: "", description: "" },
    ...(initial ? { values: initial } : {}),
  })

  const submit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await onSubmit(values)
      if (resetOnSuccess) reset({ name: "", description: "" })
    } catch (error) {
      if (error instanceof ApiError && error.code === "DEPARTMENT_NAME_TAKEN") {
        setError("name", { message: "A department with this name already exists." })
      } else if (!applyServerErrors(error, setError, ["name", "description"])) {
        setFormError(departmentErrorMessage(error))
      }
    }
  })

  return (
    <form onSubmit={submit} noValidate className="grid gap-4">
      {formError && (
        <Alert variant="destructive">
          <AlertDescription>{formError}</AlertDescription>
        </Alert>
      )}
      <TextField id="department-name" label="Name" error={errors.name} {...register("name")} />
      <TextAreaField
        id="department-description"
        label="Description"
        error={errors.description}
        {...register("description")}
      />
      <Button type="submit" disabled={isSubmitting} className="justify-self-start">
        {isSubmitting ? pendingLabel : submitLabel}
      </Button>
    </form>
  )
}
