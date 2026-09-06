#!/usr/bin/env bash

set -Eeuo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed. Install Docker Desktop or Docker Engine with Compose v2." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "The Docker engine is not running. Start Docker and run this script again." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose v2 is required. Install the Compose plugin and run this script again." >&2
  exit 1
fi

if ! command -v curl >/dev/null 2>&1; then
  echo "curl is required for the startup health check." >&2
  exit 1
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  echo "Created .env from the local-development defaults."
fi

echo "Building and starting SeatForge..."
docker compose up --build --detach

deadline=$((SECONDS + 300))
until curl --fail --silent --show-error http://localhost:8080/actuator/health >/dev/null 2>&1; do
  if (( SECONDS >= deadline )); then
    echo "SeatForge did not become healthy within five minutes." >&2
    docker compose ps
    docker compose logs --tail 80 api
    exit 1
  fi
  sleep 3
done

echo "SeatForge is ready."
echo "Web:        http://localhost:3000"
echo "API health: http://localhost:8080/actuator/health"
echo "Grafana:    http://localhost:3001"
echo "Run 'make smoke' or the documented Docker command to verify the booking journey."
