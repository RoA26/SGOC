import { Ban, Loader2 } from 'lucide-react'
import { useId, useState } from 'react'
import { Modal } from '@/components/ui/Modal'
import { getApiErrorMessage } from '@/lib/errors'
import { numeroOrden, ordenCompraService, type OrdenCompra } from '@/services/ordenCompraService'

/** Cancelación con motivo obligatorio (queda registrado con quién y cuándo). */
export function CancelarOrdenModal({
  orden,
  onClose,
  onCancelada,
}: {
  orden: OrdenCompra
  onClose: () => void
  onCancelada: (orden: OrdenCompra) => void
}) {
  const [motivo, setMotivo] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const id = useId()

  async function cancelar() {
    if (!motivo.trim()) {
      setError('Indica el motivo de la cancelación.')
      return
    }
    setEnviando(true)
    setError(null)
    try {
      onCancelada(await ordenCompraService.cancelar(orden.id, motivo.trim()))
    } catch (err) {
      setError(getApiErrorMessage(err))
      setEnviando(false)
    }
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={`Cancelar ${numeroOrden(orden.numero)}`}
      size="sm"
      dismissible={!enviando}
      footer={
        <>
          <button type="button" className="u-btn u-btn--ghost" onClick={onClose} disabled={enviando}>
            Volver
          </button>
          <button
            type="button"
            className="u-btn bg-danger text-danger-surface hover:opacity-90"
            onClick={cancelar}
            disabled={enviando}
            aria-busy={enviando}
          >
            {enviando ? <Loader2 className="size-4 animate-spin" aria-hidden="true" /> : <Ban className="size-4" aria-hidden="true" />}
            Cancelar orden
          </button>
        </>
      }
    >
      <div className="space-y-3 text-sm text-foreground-muted">
        <p>
          La orden a {orden.proveedor.razonSocial} quedará cancelada y no admitirá recepciones. Esta acción no se puede
          deshacer.
        </p>
        <label htmlFor={id} className="u-label">
          Motivo
        </label>
        <textarea
          id={id}
          value={motivo}
          onChange={(event) => setMotivo(event.target.value)}
          maxLength={500}
          rows={3}
          className="u-input h-auto py-2.5"
          autoFocus
        />
        {error && (
          <p role="alert" className="u-alert u-alert--error">
            {error}
          </p>
        )}
      </div>
    </Modal>
  )
}
