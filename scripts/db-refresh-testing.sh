#!/usr/bin/env bash

set -Eeuo pipefail

PROD_STACK="${PROD_STACK:-vps-spring-boot-maidav-lixir2}"
TEST_STACK="${TEST_STACK:-vps-spring-boot-maidavtesting-wzcwxz}"
PROD_APP_CONTAINER="${PROD_APP_CONTAINER:-vps-spring-boot-maidav-lixir2-app-1}"
PROD_DB_CONTAINER="${PROD_DB_CONTAINER:-vps-spring-boot-maidav-lixir2-db-1}"
TEST_DB_CONTAINER="${TEST_DB_CONTAINER:-vps-spring-boot-maidavtesting-wzcwxz-db-1}"
TEST_APP_CONTAINER="${TEST_APP_CONTAINER:-vps-spring-boot-maidavtesting-wzcwxz-app-1}"
PROD_DB_NAME="${PROD_DB_NAME:-${DB_NAME:-sales_db}}"
TEST_DB_NAME="${TEST_DB_NAME:-${DB_NAME:-sales_db_testing}}"
PROD_DB_USER="${PROD_DB_USER:-${DB_USER:-sales}}"
TEST_DB_USER="${TEST_DB_USER:-${DB_USER:-sales_testing}}"
PROD_UPLOAD_DIR="${PROD_UPLOAD_DIR:-/app/uploads}"
TEST_UPLOAD_DIR="${TEST_UPLOAD_DIR:-/app/uploads}"
TMP_DIR="${TMP_DIR:-/tmp/maidav-db-sync}"
TEST_HEALTHCHECK_URL="${TEST_HEALTHCHECK_URL:-https://testingmaidav.creatingsoft.net/login}"

CONFIRM_OVERWRITE_TESTING="${CONFIRM_OVERWRITE_TESTING:-false}"
SYNC_UPLOADS=true
TEST_APP_STOPPED=0

usage() {
  cat <<'EOF'
Uso:
  bash scripts/db-refresh-testing.sh --confirm-overwrite-testing

Overrides opcionales por variables de entorno:
  PROD_STACK
  TEST_STACK
  PROD_APP_CONTAINER
  PROD_DB_CONTAINER
  TEST_DB_CONTAINER
  TEST_APP_CONTAINER
  PROD_DB_NAME
  TEST_DB_NAME
  PROD_DB_USER
  TEST_DB_USER
  PROD_UPLOAD_DIR
  TEST_UPLOAD_DIR
  TMP_DIR
  TEST_HEALTHCHECK_URL

Ejemplo:
  PROD_DB_NAME="sales_db" TEST_DB_NAME="sales_db_testing" \
  PROD_DB_USER="sales" TEST_DB_USER="sales_testing" \
  TEST_HEALTHCHECK_URL="https://testingmaidav.creatingsoft.net/login" \
  bash scripts/db-refresh-testing.sh --confirm-overwrite-testing

Si queres omitir la copia de uploads:
  bash scripts/db-refresh-testing.sh --confirm-overwrite-testing --skip-uploads
EOF
}

log() {
  printf '[%s] %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*"
}

fail() {
  log "ERROR: $*"
  exit 1
}

cleanup_on_error() {
  local exit_code=$?
  if [[ $exit_code -ne 0 && $TEST_APP_STOPPED -eq 1 ]]; then
    log "Intentando volver a iniciar la app de testing por fallo intermedio..."
    docker start "$TEST_APP_CONTAINER" >/dev/null 2>&1 || true
  fi
  exit "$exit_code"
}

trap cleanup_on_error EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "No se encontro el comando requerido: $1"
}

validate_container_exists() {
  docker container inspect "$1" >/dev/null 2>&1 || fail "No existe el contenedor requerido: $1"
}

validate_safe_dir() {
  local dir_path=$1
  [[ -n "$dir_path" ]] || fail "La ruta no puede ser vacia"
  [[ "$dir_path" != "/" ]] || fail "La ruta no puede ser /"
}

validate_stack_label() {
  local container_name=$1
  local expected_stack=$2
  local actual_stack

  actual_stack="$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$container_name" 2>/dev/null || true)"
  [[ -n "$actual_stack" ]] || fail "No se pudo leer la etiqueta de stack para $container_name"
  [[ "$actual_stack" == "$expected_stack" ]] || fail "El contenedor $container_name pertenece a '$actual_stack' y no a '$expected_stack'"
}

run_sql_file() {
  local container_name=$1
  local db_user=$2
  local db_name=$3
  local sql_file=$4
  docker exec -i "$container_name" psql -U "$db_user" -d "$db_name" -v ON_ERROR_STOP=1 < "$sql_file"
}

for arg in "$@"; do
  case "$arg" in
    --confirm-overwrite-testing)
      CONFIRM_OVERWRITE_TESTING=true
      ;;
    --skip-uploads)
      SYNC_UPLOADS=false
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      fail "Argumento no reconocido: $arg"
      ;;
  esac
done

[[ "$CONFIRM_OVERWRITE_TESTING" == "true" ]] || fail "Falta confirmacion explicita. Ejecuta con --confirm-overwrite-testing"

require_command docker
require_command curl
require_command mktemp

TS="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$TMP_DIR"
ANON_SQL_FILE="$(mktemp "$TMP_DIR/anonimize-testing-XXXXXX.sql")"

cat > "$ANON_SQL_FILE" <<'SQL'
BEGIN;

UPDATE clients
SET national_id = CONCAT('TEST-', id),
    first_name = CONCAT('Cliente', id),
    last_name = 'Anonimizado',
    phone = CONCAT('000', LPAD(id::text, 7, '0')),
    address = CONCAT('Direccion testing ', id),
    email = CONCAT('cliente+', id, '@example.test'),
    birth_date = NULL,
    updated_at = CURRENT_TIMESTAMP;

UPDATE providers
SET contact_name = CONCAT('Contacto testing ', id),
    phone = CONCAT('000', LPAD(id::text, 7, '0')),
    email = CONCAT('provider+', id, '@example.test'),
    address = CONCAT('Direccion testing ', id),
    updated_at = CURRENT_TIMESTAMP;

UPDATE company_settings
SET phone = '0000000000',
    email = 'testing@example.test',
    address = 'Direccion de testing',
    updated_at = CURRENT_TIMESTAMP;

UPDATE users
SET first_name = CASE WHEN first_name IS NULL THEN NULL ELSE CONCAT('User', id) END,
    last_name = CASE WHEN last_name IS NULL THEN NULL ELSE 'Testing' END,
    phone = CASE WHEN phone IS NULL THEN NULL ELSE CONCAT('000', LPAD(id::text, 7, '0')) END,
    address = CASE WHEN address IS NULL THEN NULL ELSE CONCAT('Direccion testing ', id) END,
    birth_date = NULL,
    photo_path = NULL,
    national_id = CASE WHEN national_id IS NULL THEN NULL ELSE CONCAT('TEST-USER-', id) END;

TRUNCATE TABLE password_reset_tokens RESTART IDENTITY;

COMMIT;
SQL

log "Impacto funcional: la base actual de testing sera sobreescrita y la app de testing tendra una breve interrupcion."
log "Impacto tecnico: se requiere acceso Docker, espacio temporal en $TMP_DIR y compatibilidad PostgreSQL entre produccion y testing."
log "Base produccion: $PROD_DB_NAME"
log "Base testing: $TEST_DB_NAME"
log "Usuario DB produccion: $PROD_DB_USER"
log "Usuario DB testing: $TEST_DB_USER"
log "Sincronizar uploads: $SYNC_UPLOADS"

validate_container_exists "$PROD_APP_CONTAINER"
validate_container_exists "$PROD_DB_CONTAINER"
validate_container_exists "$TEST_DB_CONTAINER"
validate_container_exists "$TEST_APP_CONTAINER"
validate_safe_dir "$PROD_UPLOAD_DIR"
validate_safe_dir "$TEST_UPLOAD_DIR"

validate_stack_label "$PROD_APP_CONTAINER" "$PROD_STACK"
validate_stack_label "$PROD_DB_CONTAINER" "$PROD_STACK"
validate_stack_label "$TEST_DB_CONTAINER" "$TEST_STACK"
validate_stack_label "$TEST_APP_CONTAINER" "$TEST_STACK"

if [[ "$SYNC_UPLOADS" == "true" ]]; then
  log "Respaldando uploads de testing..."
  docker exec "$TEST_APP_CONTAINER" sh -lc "mkdir -p '$TEST_UPLOAD_DIR'" >/dev/null
  docker exec "$TEST_APP_CONTAINER" sh -lc "tar -C '$TEST_UPLOAD_DIR' -cf - ." > "$TMP_DIR/testing-uploads-pre-refresh-$TS.tar"

  log "Exportando uploads de produccion..."
  docker exec "$PROD_APP_CONTAINER" sh -lc "mkdir -p '$PROD_UPLOAD_DIR'" >/dev/null
  docker exec "$PROD_APP_CONTAINER" sh -lc "tar -C '$PROD_UPLOAD_DIR' -cf - ." > "$TMP_DIR/prod-uploads-$TS.tar"

  PROD_UPLOAD_COUNT="$(docker exec "$PROD_APP_CONTAINER" sh -lc "find '$PROD_UPLOAD_DIR' -type f | wc -l" | tr -d '[:space:]')"

  log "Sincronizando uploads en testing..."
  docker exec "$TEST_APP_CONTAINER" sh -lc "mkdir -p '$TMP_DIR' '$TEST_UPLOAD_DIR' && find '$TEST_UPLOAD_DIR' -mindepth 1 -exec rm -rf -- {} +"
  docker cp "$TMP_DIR/prod-uploads-$TS.tar" "$TEST_APP_CONTAINER:$TMP_DIR/prod-uploads-$TS.tar"
  docker exec "$TEST_APP_CONTAINER" sh -lc "tar -C '$TEST_UPLOAD_DIR' -xf '$TMP_DIR/prod-uploads-$TS.tar'"

  TEST_UPLOAD_COUNT="$(docker exec "$TEST_APP_CONTAINER" sh -lc "find '$TEST_UPLOAD_DIR' -type f | wc -l" | tr -d '[:space:]')"
  [[ "$PROD_UPLOAD_COUNT" == "$TEST_UPLOAD_COUNT" ]] || fail "La cantidad de archivos en uploads no coincide entre produccion ($PROD_UPLOAD_COUNT) y testing ($TEST_UPLOAD_COUNT)"
fi

log "Verificando disponibilidad de PostgreSQL..."
docker exec "$PROD_DB_CONTAINER" pg_isready -U "$PROD_DB_USER" -d "$PROD_DB_NAME"
docker exec "$TEST_DB_CONTAINER" pg_isready -U "$TEST_DB_USER" -d "$TEST_DB_NAME"

log "Verificando versiones de PostgreSQL..."
PROD_VERSION="$(docker exec "$PROD_DB_CONTAINER" psql -U "$PROD_DB_USER" -d "$PROD_DB_NAME" -tAc "SHOW server_version;")"
TEST_VERSION="$(docker exec "$TEST_DB_CONTAINER" psql -U "$TEST_DB_USER" -d "$TEST_DB_NAME" -tAc "SHOW server_version;")"
[[ -n "$PROD_VERSION" && -n "$TEST_VERSION" ]] || fail "No se pudieron leer las versiones de PostgreSQL"
log "Version produccion: $PROD_VERSION"
log "Version testing: $TEST_VERSION"

log "Generando backup previo de testing..."
docker exec "$TEST_DB_CONTAINER" sh -lc "mkdir -p '$TMP_DIR' && pg_dump -U '$TEST_DB_USER' -d '$TEST_DB_NAME' -Fc -f '$TMP_DIR/testing-pre-refresh-$TS.dump'"
docker cp "$TEST_DB_CONTAINER:$TMP_DIR/testing-pre-refresh-$TS.dump" "$TMP_DIR/testing-pre-refresh-$TS.dump"

log "Deteniendo app de testing..."
docker stop "$TEST_APP_CONTAINER" >/dev/null
TEST_APP_STOPPED=1

log "Generando dump de produccion..."
docker exec "$PROD_DB_CONTAINER" sh -lc "mkdir -p '$TMP_DIR' && pg_dump -U '$PROD_DB_USER' -d '$PROD_DB_NAME' -Fc -f '$TMP_DIR/prod-refresh-$TS.dump'"
docker cp "$PROD_DB_CONTAINER:$TMP_DIR/prod-refresh-$TS.dump" "$TMP_DIR/prod-refresh-$TS.dump"
docker cp "$TMP_DIR/prod-refresh-$TS.dump" "$TEST_DB_CONTAINER:$TMP_DIR/prod-refresh-$TS.dump"

log "Cerrando conexiones activas y limpiando esquema en testing..."
docker exec "$TEST_DB_CONTAINER" psql -U "$TEST_DB_USER" -d "$TEST_DB_NAME" -v ON_ERROR_STOP=1 -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$TEST_DB_NAME' AND pid <> pg_backend_pid();"
docker exec "$TEST_DB_CONTAINER" psql -U "$TEST_DB_USER" -d "$TEST_DB_NAME" -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

log "Restaurando dump de produccion en testing..."
docker exec "$TEST_DB_CONTAINER" pg_restore -U "$TEST_DB_USER" -d "$TEST_DB_NAME" --no-owner --no-privileges "$TMP_DIR/prod-refresh-$TS.dump"

log "Ejecutando anonimizado..."
run_sql_file "$TEST_DB_CONTAINER" "$TEST_DB_USER" "$TEST_DB_NAME" "$ANON_SQL_FILE"

log "Validando post-anonimizado..."
docker exec "$TEST_DB_CONTAINER" psql -U "$TEST_DB_USER" -d "$TEST_DB_NAME" -tAc "SELECT COUNT(*) FROM password_reset_tokens;" | tr -d '[:space:]' | grep -qx '0' || fail "password_reset_tokens no quedo vacia"

CLIENT_COUNTS="$(docker exec "$TEST_DB_CONTAINER" psql -U "$TEST_DB_USER" -d "$TEST_DB_NAME" -tAc "SELECT COUNT(*), COUNT(DISTINCT national_id) FROM clients;")"
CLIENT_TOTAL="$(printf '%s' "$CLIENT_COUNTS" | cut -d '|' -f 1 | tr -d '[:space:]')"
CLIENT_DISTINCT="$(printf '%s' "$CLIENT_COUNTS" | cut -d '|' -f 2 | tr -d '[:space:]')"
[[ "$CLIENT_TOTAL" == "$CLIENT_DISTINCT" ]] || fail "clients.national_id perdio unicidad despues del anonimizado"

log "Volviendo a iniciar la app de testing..."
docker start "$TEST_APP_CONTAINER" >/dev/null
TEST_APP_STOPPED=0

log "Validando healthcheck final..."
curl -fsSL "$TEST_HEALTHCHECK_URL" >/dev/null

log "Refresh completado correctamente."
log "Backup testing: $TMP_DIR/testing-pre-refresh-$TS.dump"
log "Dump produccion: $TMP_DIR/prod-refresh-$TS.dump"
if [[ "$SYNC_UPLOADS" == "true" ]]; then
  log "Backup uploads testing: $TMP_DIR/testing-uploads-pre-refresh-$TS.tar"
  log "Uploads produccion: $TMP_DIR/prod-uploads-$TS.tar"
fi
log "Healthcheck validado en: $TEST_HEALTHCHECK_URL"
