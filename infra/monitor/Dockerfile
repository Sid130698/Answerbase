FROM node:24-alpine
RUN apk add --no-cache postgresql-client
WORKDIR /app
COPY scripts/monitor.mjs /app/monitor.mjs
USER node
CMD ["node", "monitor.mjs"]
