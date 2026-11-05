# Build the static SPA. Build context is the repo root, for the shared package.
FROM node:22-alpine AS build
WORKDIR /app/frontend
COPY packages ../packages
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend .
RUN npm run build

# Serve it with nginx; /api is proxied to the backend so the browser sees one origin
FROM nginx:1.29-alpine
COPY frontend/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/frontend/dist /usr/share/nginx/html
EXPOSE 80
