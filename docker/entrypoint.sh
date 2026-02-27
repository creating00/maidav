#!/bin/sh
set -eu

raw_url="${SPRING_DATASOURCE_URL:-${DATABASE_URL:-}}"

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

exec java -jar /app/app.jar
