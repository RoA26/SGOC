import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Tipografías de la marca, autoalojadas (sin dependencias de CDN externas).
import '@fontsource-variable/geist'
import '@fontsource/instrument-serif/400.css'
import '@fontsource/instrument-serif/400-italic.css'
import './index.css'
import App from './App.tsx'

const rootElement = document.getElementById('root')
if (!rootElement) {
  throw new Error('No se encontró el elemento #root en index.html')
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
