import { api } from '@/lib/axios'
import { leerErrorBlob } from '@/lib/errors'

/** filename*=UTF-8''OC-00001.pdf (RFC 5987) o filename="…". */
export function nombreDeArchivo(disposicion: string): string | null {
  const extendido = /filename\*=UTF-8''([^;]+)/i.exec(disposicion)
  if (extendido?.[1]) return decodeURIComponent(extendido[1])
  const simple = /filename="?([^";]+)"?/i.exec(disposicion)
  return simple?.[1] ?? null
}

/**
 * Descarga un archivo de la API (va por Axios: lleva el token y, para el SUPER_ADMIN, la empresa)
 * y el navegador lo guarda con el nombre que indica el backend. Devuelve el nombre y las cabeceras.
 */
export async function descargarArchivo(
  url: string,
  nombrePorDefecto: string,
): Promise<{ nombre: string; cabeceras: Record<string, string> }> {
  const respuesta = await api
    .get<Blob>(url, { responseType: 'blob', timeout: 60_000 })
    .catch(async (error: unknown) => Promise.reject(await leerErrorBlob(error)))
  const cabeceras = Object.fromEntries(
    Object.entries(respuesta.headers ?? {}).map(([clave, valor]) => [clave.toLowerCase(), String(valor)]),
  )
  const nombre = nombreDeArchivo(cabeceras['content-disposition'] ?? '') ?? nombrePorDefecto
  const enlaceUrl = URL.createObjectURL(respuesta.data)
  try {
    const enlace = document.createElement('a')
    enlace.href = enlaceUrl
    enlace.download = nombre
    document.body.append(enlace)
    enlace.click()
    enlace.remove()
  } finally {
    // Tras el clic el navegador ya tiene el archivo.
    setTimeout(() => URL.revokeObjectURL(enlaceUrl), 10_000)
  }
  return { nombre, cabeceras }
}
