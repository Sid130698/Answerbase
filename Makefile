.PHONY: local up down db test test-db backend-check frontend-check support-check connector-check evaluation-check format
local:
	node scripts/start-local.mjs

up:
	docker compose up --build -d --wait

down:
	docker compose down

db:
	docker compose up -d --wait db

test: db backend-check frontend-check support-check connector-check evaluation-check

test-db: db
	node scripts/prepare-test-db.mjs

backend-check: test-db
	cd backend && ./mvnw -B verify
	python3 scripts/check-coverage.py

frontend-check:
	cd frontend && npm ci && npm run lint && npm run build

format:
	cd backend && ./mvnw spotless:apply

connector-check:
	node --test connectors/*.test.mjs

support-check:
	cd support-frontend && npm ci && npm run lint && npm run build

evaluation-check:
	node --test evals/*.test.mjs
