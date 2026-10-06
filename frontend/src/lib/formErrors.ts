import axios from 'axios'
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { getApiErrorMessage } from './errors'

interface ProblemDetailWithErrors {
  errors?: Record<string, string>
}

/**
 * Lleva los errores por campo de un ProblemDetail (400 de validación o 409 por NIT/SKU
 * duplicado, ambos con `errors: { campo: mensaje }`) a los campos del formulario.
 *
 * @returns un mensaje general si el error no corresponde a ningún campo del formulario,
 *          o `null` si ya se mostró junto a los campos.
 */
export function applyApiErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): string | null {
  if (axios.isAxiosError<ProblemDetailWithErrors>(error)) {
    const fieldErrors = error.response?.data?.errors ?? {}
    let applied = false
    for (const [field, message] of Object.entries(fieldErrors)) {
      const path = fields.find((candidate) => candidate === field)
      if (path) {
        setError(path, { type: 'server', message }, { shouldFocus: !applied })
        applied = true
      }
    }
    if (applied) return null
  }
  return getApiErrorMessage(error)
}
