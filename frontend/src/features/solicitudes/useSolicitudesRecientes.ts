import axios from 'axios'
import { useEffect, useState } from 'react'
import { getApiErrorMessage } from '@/lib/errors'
import { solicitudService, type Solicitud } from '@/services/solicitudService'

export type Recientes =
  | { estado: 'cargando' }
  | { estado: 'error'; mensaje: string }
  | { estado: 'listo'; recientes: Solicitud[] }

/**
 * Las 5 solicitudes más recientes para el inicio. El backend ya filtra: TRABAJADOR recibe solo
 * las suyas; ADMINISTRADOR_COMERCIAL, las de su empresa; SUPER_ADMIN, las de la empresa elegida o todas en
 * modo global. (Los conteos por estado llegan agregados desde /analitica.)
 */
export function useSolicitudesRecientes(): Recientes {
  const [resultado, setResultado] = useState<Recientes>({ estado: 'cargando' })

  useEffect(() => {
    const controller = new AbortController()
    solicitudService
      .listar({ page: 0, size: 5, sort: 'fecha,desc' }, controller.signal)
      .then((pagina) => setResultado({ estado: 'listo', recientes: pagina.content }))
      .catch((error: unknown) => {
        if (!axios.isCancel(error)) setResultado({ estado: 'error', mensaje: getApiErrorMessage(error) })
      })
    return () => controller.abort()
  }, [])

  return resultado
}
