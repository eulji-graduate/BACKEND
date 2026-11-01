#!/usr/bin/env bash
# local 시연 DB 초기화/복구. 비밀 파일을 생성하거나 수정하지 않는다.
set -euo pipefail

cd "$(dirname "$0")/.."

case "${1:-}" in
  init|reset) action="$1" ;;
  *) echo "Usage: bash scripts/local-db.sh {init|reset}" >&2; exit 1 ;;
esac

mysql_root() {
  docker compose exec -T mysql sh -ec '
    export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
    exec mysql --default-character-set=utf8mb4 -uroot "$MYSQL_DATABASE" "$@"
  ' sh "$@"
}

table_count="$(mysql_root --batch --skip-column-names -e \
  'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()')"

if [[ "$action" == "init" && "$table_count" != "0" ]]; then
  echo "DB already contains tables. No changes made. Use reset only to replace local demo data." >&2
  exit 1
fi

if [[ "$action" == "reset" ]]; then
  # 다른 DB를 실수로 지우지 않도록 이번 시드의 회사 코드가 있는 DB만 복구한다.
  demo_count="$(mysql_root --batch --skip-column-names -e \
    "SELECT COUNT(*) FROM company WHERE code = 'ZDEMO'")"
  if [[ "$demo_count" != "1" ]]; then
    echo "Not a seeded ZDEMO database. Reset refused; no changes made." >&2
    exit 1
  fi
  db="$(mysql_root --batch --skip-column-names -e 'SELECT DATABASE()')"
  if [[ ! "$db" =~ ^[a-zA-Z0-9_]+$ ]]; then
    echo "Unsupported database name. Reset refused." >&2
    exit 1
  fi
  case "$db" in
    mysql|sys|information_schema|performance_schema) exit 1 ;;
  esac
  # reset은 해당 DB의 모든 시연 변경을 삭제한다. Redis 볼륨은 삭제하지 않는다.
  printf 'DROP DATABASE `%s`; CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n' \
    "$db" "$db" | mysql_root
fi

mysql_root < src/main/resources/db/local/schema.sql
mysql_root < src/main/resources/db/local/data.sql
echo "Local demo DB initialized. Company code: ZDEMO (see docs/LOCAL_SETUP.md for demo accounts)."
