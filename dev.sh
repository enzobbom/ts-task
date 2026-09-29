#!/usr/bin/env bash

set -e

COMMAND="${1:-run}"

case "$COMMAND" in
  run)
    set -a
    source .env.dev
    set +a

    docker compose \
      --env-file .env.dev \
      -f docker-compose.infra.yml \
      up -d

    ./gradlew bootRun
    ;;

  infra)
    docker compose \
      --env-file .env.dev \
      -f docker-compose.infra.yml \
      up -d
    ;;

  down)
    docker compose \
      --env-file .env.dev \
      -f docker-compose.infra.yml \
      down
    ;;

  *)
    echo "Usage: ./dev.sh [run|infra|down]"
    exit 1
    ;;
esac