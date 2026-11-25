FROM node:24-alpine AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
ARG VITE_SUPPORT_ORIGIN=http://localhost:5174
ENV VITE_SUPPORT_ORIGIN=$VITE_SUPPORT_ORIGIN
ARG VITE_SUPABASE_URL
ARG VITE_SUPABASE_PUBLISHABLE_KEY
ENV VITE_SUPABASE_URL=$VITE_SUPABASE_URL
ENV VITE_SUPABASE_PUBLISHABLE_KEY=$VITE_SUPABASE_PUBLISHABLE_KEY
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine
ENV SUPPORT_ORIGIN=http://localhost:5174
ENV CUSTOMER_API_UPSTREAM=http://api:8080
COPY infra/docker/nginx.conf /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
