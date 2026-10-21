# syntax=docker/dockerfile:1

# ---- build -------------------------------------------------------------------------------
FROM node:26-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# ---- runtime: nginx as a non-root user, port 8080 ----------------------------------------
FROM nginxinc/nginx-unprivileged:1.28-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --from=build /build/dist/ /usr/share/nginx/html/
EXPOSE 8080
