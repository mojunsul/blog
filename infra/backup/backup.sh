#!/usr/bin/env bash
# 저장소 루트에서 실행: 호스트 crontab이 매일 실행한다.
set -Eeuo pipefail

COMPOSE="docker compose -f infra/docker-compose.prod.yml"
NAME="blog-$(date -u +%Y%m%dT%H%M%SZ).dump"
TMP=$(mktemp)

trap 'rm -f "$TMP"' EXIT
trap 'echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) 백업 실패"; exit 1' ERR

# 파이프로 바로 올리면 pg_dump가 중간에 실패해도 잘린 백업이 정상처럼 남아, 파일로 받고 성공한 뒤에만 올린다.
$COMPOSE exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$TMP"

# 서버 권한에 삭제가 없어 미완료 멀티파트 업로드를 지울 수 없으므로 한 번에 올린다.
$COMPOSE run --rm -T -v "$TMP:/tmp/$NAME:ro" backup \
	os object put --bucket-name blog-backup --name "$NAME" --file "/tmp/$NAME" --no-overwrite --no-multipart > /dev/null

echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) 백업 완료: $NAME ($(wc -c < "$TMP" | tr -d ' ') bytes)"
