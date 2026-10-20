/** Contratos de la API de autenticación (reflejan los DTO de Spring Boot). */

/**
 * Roles del backend (enum Rol):
 * - SUPER_ADMIN: administración global de la plataforma, sin empresa. Elige contexto con X-Tenant-ID.
 * - ADMINISTRADOR_COMERCIAL: administra su empresa (trabajadores, invitaciones, catálogos, revisión de solicitudes).
 * - TRABAJADOR: crea solicitudes en su empresa y consulta las suyas.
 * Hasta la migración V8 se llamaban GERENTE y USUARIO (ver store/authStore.ts).
 */
export type Rol = 'SUPER_ADMIN' | 'ADMINISTRADOR_COMERCIAL' | 'TRABAJADOR'

export interface LoginRequest {
  username: string
  password: string
}

/** RegistroRequestDTO: alta con un código de invitación de un solo uso. */
export interface RegistroRequest {
  nombres: string
  apellidos: string
  username: string
  email: string
  password: string
  codigoInvitacion: string
}

export interface UsuarioResponse {
  id: number
  username: string
  email: string
  /** Nombre visible: "nombres apellidos", o el nombre completo de una cuenta anterior sin completar. */
  nombre: string
  /** Nulos en cuentas anteriores a V9 que aún no completaron su perfil. */
  nombres?: string | null
  apellidos?: string | null
  datosPersonalesCompletos: boolean
  rol: Rol
  /** Empresa del usuario (leída de la BD en cada petición); el backend la omite para SUPER_ADMIN. */
  empresaId?: number
  empresaNombre?: string
  empresaCodigo?: string
  /** PENDIENTE tras registrarse con el código de empresa, hasta que un administrador lo apruebe. */
  estado?: EstadoUsuario
}

export type EstadoUsuario = 'PENDIENTE' | 'ACTIVO' | 'RECHAZADO' | 'INACTIVO'

export interface LoginResponse {
  accessToken: string
  tokenType: 'Bearer'
  /** Segundos de validez del token. */
  expiresIn: number
  usuario: UsuarioResponse
}

export interface DatosPersonales {
  nombres: string
  apellidos: string
}

export type EstadoInvitacion = 'VIGENTE' | 'USADA' | 'REVOCADA' | 'CADUCADA'

/** UsuarioResumenDTO */
export interface UsuarioResumen {
  id: number
  username: string
  nombre: string
}

/** InvitacionResumenDTO: nunca trae el código completo, solo sus 4 últimos caracteres. */
export interface InvitacionResumen {
  id: number
  codigoParcial: string
  estado: EstadoInvitacion
  creadoEn: string
  fechaExpiracion: string
  creador: UsuarioResumen
  usadoPor?: UsuarioResumen
  fechaUso?: string
  revocadoPor?: UsuarioResumen
  fechaRevocacion?: string
}

/** InvitacionResponseDTO: el código solo se devuelve una vez, al generarlo. */
export interface InvitacionResponse {
  /** Formato XXXX-XXXX-XXXX-XXXX. */
  codigo: string
  /** Instante ISO-8601 (UTC) a partir del cual el código deja de valer. */
  fechaExpiracion: string
}
