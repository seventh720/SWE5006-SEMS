FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY src ./src
COPY public ./public
COPY index.html tsconfig*.json vite.config.ts ./
RUN npm run build

FROM nginx:stable-alpine AS runtime
RUN apk upgrade --no-cache
COPY nginx-main.conf /etc/nginx/nginx.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
    CMD wget -q --spider http://127.0.0.1/healthz || exit 1

FROM runtime AS ci
COPY dist/ /usr/share/nginx/html/

FROM runtime AS local
COPY --from=build /workspace/dist /usr/share/nginx/html
