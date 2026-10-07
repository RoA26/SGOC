import type { Config } from 'tailwindcss'

/**
 * Tema de Tailwind conectado a la marca Unisen.
 *
 * Ningún color o tipografía está escrito aquí: cada clase apunta a una variable
 * CSS de `src/brand/unisen/tokens.css` o `componentes.css`. Así, `bg-primary`
 * genera `background-color: var(--unisen-color-primary)`, el modo oscuro llega
 * gratis desde los tokens y cambiar la marca no exige recompilar este archivo.
 *
 * Las opacidades siguen funcionando (`bg-primary/10`) gracias a color-mix().
 */
const token = (name: string): string => `var(--unisen-${name})`

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        background: token('color-bg'),
        surface: {
          DEFAULT: token('color-surface'),
          muted: token('color-surface-muted'),
        },
        border: {
          DEFAULT: token('color-border'),
          strong: token('color-border-strong'),
        },
        foreground: {
          DEFAULT: token('color-text'),
          muted: token('color-text-muted'),
        },
        primary: {
          DEFAULT: token('color-primary'),
          hover: token('color-primary-hover'),
          foreground: token('color-on-primary'),
        },
        accent: token('color-accent'),
        brand: {
          panel: token('color-brand-panel'),
          'panel-foreground': token('color-brand-panel-text'),
          'panel-muted': token('color-brand-panel-muted'),
        },
        danger: {
          DEFAULT: token('color-danger'),
          surface: token('color-danger-surface'),
          border: token('color-danger-border'),
        },
        success: {
          DEFAULT: token('color-success'),
          surface: token('color-success-surface'),
          border: token('color-success-border'),
        },
        focus: token('color-focus'),
      },
      fontFamily: {
        // Geist (texto e interfaz) e Instrument Serif (titulares editoriales).
        sans: token('font-sans'),
        serif: token('font-serif'),
        mono: token('font-mono'),
      },
      borderRadius: {
        sm: token('radius-sm'),
        md: token('radius-md'),
        lg: token('radius-lg'),
        xl: token('radius-xl'),
      },
      boxShadow: {
        sm: token('shadow-sm'),
        card: token('shadow-card'),
      },
      height: {
        control: token('control-height'),
      },
    },
  },
} satisfies Config
