# Etapa 1: compila el frontend. En un VPS con poca RAM se puede compilar en otra máquina
# y cargar la imagen con docker save/load (ver docs/despliegue.md).
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# Etapa 2: solo Nginx y los estáticos.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy_headers.inc /etc/nginx/conf.d/proxy_headers.inc
COPY --from=build /app/dist /usr/share/nginx/html
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://127.0.0.1/healthz || exit 1
