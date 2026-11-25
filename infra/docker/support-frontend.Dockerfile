FROM node:24-alpine AS build
WORKDIR /app
COPY support-frontend/package*.json ./
RUN npm ci
COPY support-frontend/ ./
ARG VITE_SUPABASE_URL
ARG VITE_SUPABASE_PUBLISHABLE_KEY
ARG VITE_CUSTOMER_ORIGIN=http://localhost:5173
ENV VITE_SUPABASE_URL=$VITE_SUPABASE_URL VITE_SUPABASE_PUBLISHABLE_KEY=$VITE_SUPABASE_PUBLISHABLE_KEY VITE_CUSTOMER_ORIGIN=$VITE_CUSTOMER_ORIGIN
RUN npm run build
FROM nginxinc/nginx-unprivileged:alpine
ENV SUPPORT_API_UPSTREAM=http://api:8080
COPY infra/docker/support-nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
