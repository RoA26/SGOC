// En el paquete de marca, el nombre indica el tema en que se usa: «claro» va sobre fondos claros
// (borgoña y tinta) y «oscuro» sobre fondos oscuros (rosa y crema).
import logoFondoClaro from '@/brand/nexora/nexora-logo-claro.svg'
import logoFondoOscuro from '@/brand/nexora/nexora-logo-oscuro.svg'

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
    return <img src={variant === 'claro' ? logoFondoOscuro : logoFondoClaro} alt="Nexora" className={className} />
  }
  return (
    <picture>
      <source srcSet={logoFondoOscuro} media="(prefers-color-scheme: dark)" />
      <img src={logoFondoClaro} alt="Nexora" className={className} />
    </picture>
  )
}
