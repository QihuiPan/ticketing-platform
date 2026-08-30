.PHONY: test build up down logs load-test

test:
	./mvnw verify

build:
	./mvnw clean package
	cd web && npm ci && npm run build

up:
	docker compose up --build -d

down:
	docker compose down --remove-orphans

logs:
	docker compose logs -f api notification-worker web

load-test:
	docker compose run --rm k6 run /scripts/hold-contention.js
