/** Página de resultados tal como la serializa Spring Data (modo VIA_DTO). */
export interface Page<T> {
  content: T[]
  page: PageMetadata
}

export interface PageMetadata {
  /** Tamaño de página solicitado. */
  size: number
  /** Número de página, empezando en 0. */
  number: number
  totalElements: number
  totalPages: number
}

export interface PageParams {
  page: number
  size: number
  /** "campo,asc|desc"; admite varios criterios. */
  sort?: string | string[]
}
