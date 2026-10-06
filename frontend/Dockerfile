# syntax=docker/dockerfile:1.7
# =============================================================================
# Unisen SGP - Frontend React servido por Nginx
#   docker build -t unisen/sgp-frontend ./frontend
# =============================================================================

# ---------- Etapa 1: build con Node ----------
FROM node:22-alpine AS build
WORKDIR /app

# Dependencias en una capa propia: solo se reinstalan si cambia el lockfile.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
# Ruta relativa por defecto: el navegador llama al mismo origen (Nginx → spring-backend).
ARG VITE_API_URL=/api
ENV VITE_API_URL=${VITE_API_URL}
RUN npm run build

# ---------- Etapa 2: Nginx sirve los estáticos y hace de proxy de /api ----------
FROM nginx:1.30-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1/healthz || exit 1
