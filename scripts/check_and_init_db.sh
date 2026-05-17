#!/usr/bin/env bash

set -euo pipefail

# Database connectivity and initialization script
# Purpose:
# 1. Check MySQL and Redis connectivity
# 2. Initialize the database and table structure from the project SQL
#
# Usage:
#   bash scripts/check_and_init_db.sh --check
#   bash scripts/check_and_init_db.sh --init
#
# Overrideable environment variables:
#   DB_HOST DB_PORT DB_NAME DB_USER DB_PASSWORD
#   REDIS_HOST REDIS_PORT REDIS_DB

MODE="${1:-"--check"}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEMA_SQL="${ROOT_DIR}/doc/mysql_schema.sql"

DB_HOST="${DB_HOST:-222.201.187.184}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-class_deduction_system}"
DB_USER="${DB_USER:-whw}"
DB_PASSWORD="${DB_PASSWORD:-123456}"

REDIS_HOST="${REDIS_HOST:-222.201.187.184}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_DB="${REDIS_DB:-0}"

if [[ ! -f "${SCHEMA_SQL}" ]]; then
  echo "Schema SQL file not found: ${SCHEMA_SQL}" >&2
  exit 1
fi

if ! command -v mysql >/dev/null 2>&1; then
  echo "mysql client not found. Please install it first." >&2
  exit 1
fi

if ! command -v redis-cli >/dev/null 2>&1; then
  echo "redis-cli client not found. Please install it first." >&2
  exit 1
fi

MYSQL_BASE_ARGS=(
  -h "${DB_HOST}"
  -P "${DB_PORT}"
  -u "${DB_USER}"
  "--password=${DB_PASSWORD}"
  --connect-timeout=5
  --default-character-set=utf8mb4
)

echo "Checking MySQL connectivity..."
MYSQL_VERSION="$(
  mysql "${MYSQL_BASE_ARGS[@]}" \
    -Nse "SELECT VERSION();" 2>/tmp/class_deduction_mysql_err.log
)"
echo "MySQL connected successfully. Version: ${MYSQL_VERSION}"

echo "Checking Redis connectivity..."
REDIS_PING="$(
  redis-cli \
    -h "${REDIS_HOST}" \
    -p "${REDIS_PORT}" \
    -n "${REDIS_DB}" \
    ping 2>/tmp/class_deduction_redis_err.log
)"

if [[ "${REDIS_PING}" != "PONG" ]]; then
  echo "Redis connectivity check failed. Response: ${REDIS_PING}" >&2
  exit 1
fi

echo "Redis connected successfully. Response: ${REDIS_PING}"

if [[ "${MODE}" == "--check" ]]; then
  echo "Connectivity checks completed."
  exit 0
fi

if [[ "${MODE}" != "--init" ]]; then
  echo "Unsupported mode: ${MODE}. Available modes: --check / --init" >&2
  exit 1
fi

echo "Initializing the database and table structure..."
mysql "${MYSQL_BASE_ARGS[@]}" < "${SCHEMA_SQL}"
echo "Database initialization completed."
