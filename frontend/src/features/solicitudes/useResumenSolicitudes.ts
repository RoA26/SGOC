import axios from 'axios'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '@/lib/errors'
import { solicitudService, type EstadoSolicitud, type Solicitud } from '@/services/solicitudService'

export type Resumen =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  | { estado: 'listo'; conteos: Record<EstadoSolicitud, number>; recientes: Solicitud[] }

/**
 * Datos del panel de inicio con el endpoint real de solicitudes: un total por estado
 * (`size=1`, se lee page.totalElements) y las 5 más recientes. El backend ya filtra: USUARIO
 * recibe solo las suyas; GERENTE, las de su empresa; SUPER_ADMIN, las de la empresa elegida o
 * todas en modo global.
 */
export function useResumenSolicitudes(): Resumen {
  const [resumen, setResumen] = useState<Resumen>({ estado: 'cargando' })

  useEffect(() => {
    const controller = new AbortController()
    const { signal } = controller
    const total = (estado: EstadoSolicitud) => solicitudService.listar({ page: 0, size: 1, estado }, signal)
    Promise.all([
      total('PENDIENTE'),
      total('APROBADA'),
      total('RECHAZADA'),
      solicitudService.listar({ page: 0, size: 5, sort: 'fecha,desc' }, signal),
    ])
      .then(([pendientes, aprobadas, rechazadas, recientes]) =>
        setResumen({
          estado: 'listo',
          conteos: {
            PENDIENTE: pendientes.page.totalElements,
            APROBADA: aprobadas.page.totalElements,
            RECHAZADA: rechazadas.page.totalElements,
          },
          recientes: recientes.content,
        }),
      )
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setResumen({ estado: 'error', mensaje: getApiErrorMessage(error) })
      })
    return () => controller.abort()
  }, [])

  return resumen
}
