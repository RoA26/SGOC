import logoClaro from '@/brand/unisen/unisen-logo-claro.svg'
import logoOscuro from '@/brand/unisen/unisen-logo-oscuro.svg'

interface BrandLogoProps {
  /**
   * - `claro`: logotipo claro, para fondos oscuros (p. ej. el panel de marca).
   * - `oscuro`: logotipo oscuro, para fondos claros.
   * - `auto`: elige según el tema del sistema.
   */
  variant?: 'auto' | 'claro' | 'oscuro'
  className?: string
}

export function BrandLogo({ variant = 'auto', className }: BrandLogoProps) {
  if (variant !== 'auto') {
    return <img src={variant === 'claro' ? logoClaro : logoOscuro} alt="Unisen" className={className} />
  }
  return (
    <picture>
      <source srcSet={logoClaro} media="(prefers-color-scheme: dark)" />
      <img src={logoOscuro} alt="Unisen" className={className} />
    </picture>
  )
}
