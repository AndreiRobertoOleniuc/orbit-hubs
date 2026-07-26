# syntax=docker/dockerfile:1.7

FROM node:24-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Vite values are compiled into the static bundle. The Clerk key is public,
# but a BuildKit secret avoids persisting it in Docker build metadata.
# An empty API base URL makes the browser call the same host at /api/v1.
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN --mount=type=secret,id=vite_clerk_publishable_key,required=true \
    VITE_CLERK_PUBLISHABLE_KEY="$(cat /run/secrets/vite_clerk_publishable_key)" \
    npm run build

FROM nginx:1.30-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1/healthz || exit 1
