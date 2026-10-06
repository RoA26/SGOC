import { fileURLToPath, URL } from 'node:url'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true,
    // En desarrollo /api se reenvía al backend local, igual que hace Nginx en producción:
    // el frontend siempre usa rutas relativas y no depende de CORS.
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
})
