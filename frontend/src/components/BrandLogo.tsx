// En el paquete de marca, el nombre indica el tema en que se usa: «claro» va sobre fondos claros
// (borgoña y tinta) y «oscuro» sobre fondos oscuros (rosa y crema).
import logoFondoClaro from '@/brand/nexora/nexora-logo-claro.svg'
import logoFondoOscuro from '@/brand/nexora/nexora-logo-oscuro.svg'
import { useTema } from '@/lib/tema'

interface BrandLogoProps {
  /**
   * - `claro`: logotipo claro, para fondos oscuros (p. ej. el panel de marca).
   * - `oscuro`: logotipo oscuro, para fondos claros.
   * - `auto`: elige según el tema actual (el del sistema o el elegido con el alternador).
   */
  variant?: 'auto' | 'claro' | 'oscuro'
  className?: string
}

export function BrandLogo({ variant = 'auto', className }: BrandLogoProps) {
  const tema = useTema()
  const claro = variant === 'auto' ? tema === 'dark' : variant === 'claro'
  return <img src={claro ? logoFondoOscuro : logoFondoClaro} alt="Nexora" className={className} />
}
