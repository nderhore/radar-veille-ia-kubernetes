# syntax=docker/dockerfile:1
# --- Étape 1 : construction de l'application Angular ---
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# --- Étape 2 : nginx non privilégié (port 8080, utilisateur 101) ---
FROM nginxinc/nginx-unprivileged:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/radar-web/browser /usr/share/nginx/html
EXPOSE 8080
