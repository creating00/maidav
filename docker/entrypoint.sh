#!/bin/sh
set -eu

raw_url="${SPRING_DATASOURCE_URL:-${DATABASE_URL:-}}"
port="${PORT:-10000}"
upload_dir="${APP_UPLOAD_DIR:-/app/uploads}"

if [ -n "${raw_url}" ]; then
  normalized_url="${raw_url}"
  case "${raw_url}" in
    postgres://*)
      normalized_url="jdbc:postgresql://${raw_url#postgres://}"
      ;;
    postgresql://*)
      normalized_url="jdbc:postgresql://${raw_url#postgresql://}"
      ;;
    jdbc:postgresql://*)
      normalized_url="${raw_url}"
      ;;
  esac

  # If URL includes user info (user:pass@host), strip it; credentials are provided via env vars.
  url_without_prefix="${normalized_url#jdbc:postgresql://}"
  case "${url_without_prefix}" in
    *@*)
      normalized_url="jdbc:postgresql://${url_without_prefix#*@}"
      ;;
  esac

  export SPRING_DATASOURCE_URL="${normalized_url}"
fi

mkdir -p "${upload_dir}"

echo "Starting Maidav on 0.0.0.0:${port}"
echo "Uploads directory: ${upload_dir}"

exec java \
  -Dserver.address=0.0.0.0 \
  -Dserver.port="${port}" \
  -jar /app/app.jar
