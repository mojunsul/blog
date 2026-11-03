#!/usr/bin/env bash
# 서버에서 실행: deploy 워크플로가 .env.next를 보낸 뒤 배포할 커밋의 이 스크립트를 실행한다.
set -Eeuo pipefail

SHA=$1
cd "$HOME/blog"
COMPOSE="docker compose -f infra/docker-compose.prod.yml"
PREV=$(git rev-parse HEAD)

# 마운트한 Nginx 설정 파일은 git이 교체해도 up -d·reload로 반영되지 않아 컨테이너를 다시 만든다.
apply() {
	$COMPOSE up -d --no-build
	if ! git diff --quiet "$1" "$2" -- infra/nginx; then
		$COMPOSE up -d --no-deps --force-recreate nginx
	fi
}

# start_period 60초 + retries x interval 에 여유를 둔 180초. Nginx resolver 캐시(10초) 동안의 502는 재시도로 넘긴다.
check() {
	local i url
	for i in $(seq 36); do
		[ "$($COMPOSE ps --format '{{.Health}}' backend)" = healthy ] && break
		[ "$i" = 36 ] && return 1
		sleep 5
	done
	for url in https://localhost/ https://localhost/api/v1/posts; do
		for i in $(seq 6); do
			curl -fsSk -o /dev/null "$url" && continue 2
			[ "$i" = 6 ] && return 1
			sleep 5
		done
	done
}

rollback() {
	trap - ERR
	set +e
	echo "배포 실패: $PREV 로 롤백"
	$COMPOSE logs --tail 50 backend frontend
	git checkout -q --detach "$PREV"
	mv infra/.env.prev infra/.env
	apply "$SHA" "$PREV"
	check && echo "롤백 후 헬스체크 통과" || echo "롤백 후에도 헬스체크 실패"
	exit 1
}
trap rollback ERR

cp infra/.env infra/.env.prev
git checkout -q --detach "$SHA"
(umask 077; { cat infra/.env.next; printf 'IMAGE_TAG=%s\n' "$SHA"; } > infra/.env)
rm infra/.env.next

$COMPOSE pull backend frontend
apply "$PREV" "$SHA"
check

rm infra/.env.prev
docker image prune -af
