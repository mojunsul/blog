## 12. 인프라와 배포

### 12-1. 로컬 개발
- `infra/docker-compose.yml`로 PostgreSQL만 실행
- 시드 데이터는 `local` 프로필로 처음 기동할 때 들어간다. 다시 넣으려면 `docker compose down -v`로 DB 볼륨을 지운 뒤 재기동한다.
- Spring Boot(IDE, `local` 프로필), Next.js(`npm run dev`)는 직접 실행

### 12-2. 서버 (OCI)
- `docker-compose.prod.yml`: nginx, certbot, frontend, backend, postgres, backup
- PostgreSQL 데이터는 named volume으로 영속화
- 비밀값은 서버의 `infra/.env`에만 둔다 (원본은 GitHub Secrets에서 관리)
  - `POSTGRES_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, Object Storage 인증 정보 등
  - `JWT_SECRET`은 `openssl rand -base64 32`로 만든다 (Base64, 디코딩 후 32바이트 이상, 10-6). 바꾸면 발급된 Access 토큰이 모두 무효가 된다.
  - 관리자 비밀번호를 바꾸려면 `admin` 행을 삭제하고 `ADMIN_PASSWORD`를 바꾼 뒤 backend를 재시작한다 (5장 '관리자 계정 생성'). 절차는 M3에서 README에 적는다.
- OCI 보안 목록 + 인스턴스 iptables 양쪽에서 80/443 개방 (22는 본인 IP로 제한 권장)
- 예약 공인 IP(Reserved Public IP) 사용
- 모든 서비스에 `restart: unless-stopped`
- backend에 `healthcheck` 설정 (`/actuator/health`), nginx·frontend는 backend healthy 이후 기동
- **로그 용량 제한** (디스크가 작아서 필수)
  ```yaml
  logging:
    driver: json-file
    options:
      max-size: "10m"
      max-file: "3"
  ```

### 12-3. HTTPS (Nginx + Certbot)
1. Nginx를 80 포트로 실행, `/.well-known/acme-challenge/` 경로 개방
2. Certbot 컨테이너로 인증서 발급, 공유 볼륨에 저장
3. 443 server 블록 추가, HTTP → HTTPS 리다이렉트, Nginx reload
4. Certbot 주기적 갱신 + 갱신 후 Nginx reload

### 12-4. Nginx 설정 요점
- `conf.d/blog.conf`
  - `/api/` → `proxy_pass http://backend:8080;`
  - `/` → `proxy_pass http://frontend:3000;`
  - 프록시 헤더: `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`, `X-Request-Id`(`$request_id`, 클라이언트가 보낸 값을 덮어씀)
  - 접근 로그 형식에 `$request_id`를 포함해 앱 로그와 연결한다.
- `nginx.conf`
  - gzip 압축
  - rate limit: 로그인 `/api/v1/auth/login` (예: IP당 분당 5회), 조회수 API, 그 외 `/api/` 일반 제한
  - `client_max_body_size`는 1MB 이상으로 둔다 (글 작성·수정 요청 본문은 `content` 최대 100,000자 기준 약 600KB 이하, 8-4). 기본값 1MB를 그대로 쓰지 않고 명시한다.
- 보안 헤더: HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options`
- `server_tokens off;`

### 12-5. DB 백업
- `backup` 컨테이너(또는 호스트 cron)가 매일 `pg_dump` 실행 → gzip 압축 → OCI Object Storage 업로드
- 보관 정책: 최근 14일 (Object Storage 수명 주기 규칙 또는 스크립트로 정리)
- **복구 절차를 README에 문서화하고, 최소 한 번은 실제로 복구 테스트를 한다.**

### 12-6. CI/CD
```
PR
 ├ backend-ci : ./gradlew test (Testcontainers), build
 └ frontend-ci: lint, typecheck, test(Vitest), build (백엔드 없이 성공해야 함)

main 머지 (deploy.yml)
 → docker buildx로 linux/arm64 이미지 빌드 (backend, frontend)
 → GHCR push (태그: 커밋 SHA + latest)
 → SSH 접속 → docker compose pull && docker compose up -d
 → 내부 헬스체크 확인, 실패 시 이전 SHA 태그로 롤백
```
- Flyway 마이그레이션은 backend 기동 시 자동 실행된다. 마이그레이션은 이전 버전 코드와도 호환되게 작성한다 (컬럼 삭제는 코드 배포 이후 별도 마이그레이션으로).
- `compose up -d` 방식은 수 초의 다운타임이 있다. 무중단 배포는 M6 이후 학습 주제로 둔다.

---

