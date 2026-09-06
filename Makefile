.PHONY: test build quickstart up down logs status smoke load-test

test:
	./mvnw verify

build:
	./mvnw clean package
	cd web && npm ci && npm run build

quickstart:
	./scripts/quickstart.sh

up:
	docker compose up --build -d

down:
	docker compose down --remove-orphans

logs:
	docker compose logs -f api notification-worker web

status:
	docker compose ps

smoke:
	docker compose --profile load-test run --rm k6 run /scripts/smoke.js

load-test:
	docker compose run --rm k6 run /scripts/hold-contention.js
