interface ImportMetaEnv {
  /** URL base de la API. Por defecto "/api" (mismo origen, vía Nginx o el proxy de Vite). */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
