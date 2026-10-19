import { Modal } from '@/components/ui/Modal'
import { RegistroRecepcion } from '@/features/entregas/RegistroRecepcion'
import { numeroOrden, type OrdenCompra, type Recepcion } from '@/services/ordenCompraService'

/**
 * Registro de mercancía desde la ficha de la orden (administrador). Usa el mismo formulario que
 * la vista del trabajador: unidades recibidas, dañadas y faltantes por línea, otras novedades y un
 * resumen antes de confirmar.
 */
export function RecepcionModal({
  orden,
  onClose,
  onRegistrada,
}: {
  orden: OrdenCompra
  onClose: () => void
  onRegistrada: (recepcion: Recepcion) => void
}) {
  return (
    <Modal
      open
      onClose={onClose}
      title={`Registrar recepción · ${numeroOrden(orden.numero)}`}
      description={`${orden.proveedor.razonSocial}. Lo recibido en buen estado se suma a las existencias y queda en el kárdex.`}
      size="xl"
    >
      <RegistroRecepcion ordenId={orden.id} lineas={orden.detalles} onRegistrada={onRegistrada} onCancelar={onClose} />
    </Modal>
  )
}
