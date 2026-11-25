.PHONY: local up down db test backend-check frontend-check format
local:
	node scripts/start-local.mjs

up:
	docker compose up --build -d --wait

down:
	docker compose down

db:
	docker compose up -d --wait db

test: db backend-check frontend-check

backend-check:
	cd backend && ./mvnw -B verify

frontend-check:
	cd frontend && npm ci && npm run lint && npm run build

format:
	cd backend && ./mvnw spotless:apply
