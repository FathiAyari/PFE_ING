# syntax=docker/dockerfile:1.7

# ---- build stage ----
FROM node:20-alpine AS build
WORKDIR /app

# Install deps (cache layer)
COPY package*.json ./
RUN npm ci

# Build the SPA
COPY . .
RUN npx ng build --configuration production

# ---- runtime stage (nginx) ----
FROM nginx:1.27-alpine AS runtime

# Strip default config; provide ours
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/app.conf

# Angular 19 outputs to dist/<project>/browser
COPY --from=build /app/dist/pfe_front/browser /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
