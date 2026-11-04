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

# 호스트 uid로 실행하는 이유: 임시 파일이 호스트 사용자 소유 600이라 이미지 사용자(uid 1000)와 uid가 다르면 읽지 못한다.
# 서버 권한에 삭제가 없어 미완료 멀티파트 업로드를 지울 수 없으므로 한 번에 올린다.
$COMPOSE run --rm -T --user "$(id -u):$(id -g)" -v "$TMP:/tmp/$NAME:ro" backup \
	os object put --bucket-name blog-backup --name "$NAME" --file "/tmp/$NAME" --no-overwrite --no-multipart > /dev/null

echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) 백업 완료: $NAME ($(wc -c < "$TMP" | tr -d ' ') bytes)"
