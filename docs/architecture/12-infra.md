## 12. 인프라와 배포

### 12-1. 로컬 개발
- `infra/docker-compose.yml`로 PostgreSQL만 실행
- 시드 데이터는 `local` 프로필로 처음 기동할 때 들어간다. 다시 넣으려면 `docker compose down -v`로 DB 볼륨을 지운 뒤 재기동한다.
- Spring Boot(IDE, `local` 프로필), Next.js(`npm run dev`)는 직접 실행

### 12-2. 서버 (OCI)
- `docker-compose.prod.yml`: nginx, certbot, frontend, backend, postgres, backup (certbot은 HTTPS 이슈, backup은 백업 이슈에서 추가)
  - 최상위 `name: blog-prod`. 로컬용 `docker-compose.yml`과 같은 `infra/`에 있어 이름을 주지 않으면 둘 다 프로젝트 이름이 `infra`가 되고, 볼륨(`infra_postgres-data`)을 함께 써 로컬 시드 DB로 운영 구성이 뜬다.
  - 이미지: `image: ghcr.io/mojunsul/blog-backend:${IMAGE_TAG:-latest}`(frontend는 `blog-frontend`)와 `build: ../backend`(`../frontend`)를 함께 둔다. 로컬은 `IMAGE_TAG=local`로 `up -d --build`, 서버는 `pull` 후 `up -d --no-build`(소스가 없어도 `build`는 빌드할 때만 읽힌다). deploy(12-6)도 이 이름으로 push한다.
  - 호스트에 여는 포트는 nginx `80:80`뿐이다 (HTTPS 이슈에서 443 추가). postgres·backend·frontend에는 `ports`를 두지 않는다 (같은 compose 네트워크에서 서비스 이름으로 접속).
  - postgres: `postgres:18`(로컬과 같다), `POSTGRES_DB`·`POSTGRES_USER`·`POSTGRES_PASSWORD`·`TZ: UTC`, named volume `postgres-data:/var/lib/postgresql`, `pg_isready` healthcheck(로컬과 같다)
  - backend: `DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-blog}`과 12-7의 나머지 환경변수를 `infra/.env`에서 받는다. postgres healthy 이후 기동.
  - frontend: `API_INTERNAL_URL: http://backend:8080` (11장 'API 주소 처리')
- PostgreSQL 데이터는 named volume으로 영속화
- 비밀값은 서버의 `infra/.env`에만 둔다 (원본은 GitHub Secrets에서 관리)
  - `POSTGRES_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, Object Storage 인증 정보 등
  - 비밀값이 아닌 `IMAGE_TAG`(비우면 `latest`)도 `infra/.env.example`에 둔다. 로컬은 셸에서 `IMAGE_TAG=local`로 덮어쓴다.
  - `JWT_SECRET`은 `openssl rand -base64 32`로 만든다 (Base64, 디코딩 후 32바이트 이상, 10-6). 바꾸면 발급된 Access 토큰이 모두 무효가 된다.
  - 관리자 비밀번호를 바꾸려면 `admin` 행을 삭제하고 `ADMIN_PASSWORD`를 바꾼 뒤 backend를 재시작한다 (5장 '관리자 계정 생성'). 절차는 M3에서 README에 적는다.
- OCI 보안 목록 + 인스턴스 iptables 양쪽에서 80/443 개방 (22는 본인 IP로 제한 권장)
- 예약 공인 IP(Reserved Public IP) 사용
- 모든 서비스에 `restart: unless-stopped`
- backend에 `healthcheck` 설정 (`/actuator/health`), nginx·frontend는 backend healthy 이후 기동 (`depends_on: condition: service_healthy`)
  - `curl -fsS http://localhost:8080/actuator/health`(JRE 이미지에 `curl`이 있다, 12-7), `interval: 10s`, `timeout: 3s`, `retries: 5`, `start_period: 60s`(작은 서버에서 JVM 기동과 Flyway가 끝나기 전 실패를 세지 않게)
- **로그 용량 제한** (디스크가 작아서 필수). 모든 서비스에 같은 값을 `x-logging` 앵커로 한 번 정의해 붙인다.
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
3. 443 server 블록 추가(HSTS 헤더 포함), HTTP → HTTPS 리다이렉트, Nginx reload
4. Certbot 주기적 갱신 + 갱신 후 Nginx reload

### 12-4. Nginx 설정 요점
nginx 이미지는 `nginx:1-alpine`(12-7과 같은 메이저 태그 원칙). `infra/nginx/nginx.conf`를 `/etc/nginx/nginx.conf`로, `infra/nginx/conf.d/`를 `/etc/nginx/conf.d/`로 읽기 전용 마운트한다. 접근·오류 로그는 이미지 기본대로 표준 출력·표준 오류로 나가 Docker 로그 제한(12-2)을 받는다.

- `nginx.conf` (`http` 블록)
  - `server_tokens off;` (`Server: nginx`만 남는다. 업스트림의 `Server` 헤더는 Nginx가 기본으로 숨긴다)
  - 접근 로그 형식: 기본 `combined` 뒤에 `rt=$request_time rid=$request_id`를 붙인 `main`
  - gzip: `gzip on`, `gzip_types`에 `application/json`·`text/css`·`application/javascript`·`image/svg+xml`, `gzip_min_length 1024`. 주 대상은 `/api` JSON이다 (Spring은 압축하지 않는다). Next 응답은 Next가 직접 압축해 `Content-Encoding`이 있어 Nginx가 다시 압축하지 않는다.
  - `client_max_body_size 2m;` — 글 작성·수정 요청 본문은 `content` 최대 100,000자 기준 약 600KB 이하다 (8-4). 기본값 1MB를 그대로 쓰지 않고 여유를 두어 명시한다.
  - rate limit zone (키 `$binary_remote_addr`, IP당)
    | zone | 대상 | `rate` | `burst` |
    |---|---|---|---|
    | `login` (1m) | `/api/v1/auth/login` | `5r/m` | `5 nodelay` |
    | `api` (10m) | 그 외 `/api/` | `10r/s` | `20 nodelay` |
    - 로그인: 연속 6회(1 + burst)까지 바로 처리하고, 그 뒤는 12초에 1회. 관리자 1명이 비밀번호를 몇 번 틀리는 정도는 막히지 않고, 대입 공격은 분당 5회로 줄어든다.
    - `api`: 서버 컴포넌트의 API 호출은 Nginx를 거치지 않으므로(`http://backend:8080`, 3장) 브라우저가 직접 부르는 API(관리자 화면, M6 조회수)만 받는다. 관리자 화면 한 번에 몇 건이라 여유가 크다. 조회수 zone은 M6에서 추가한다.
    - `limit_req_status 429;` (기본값 `503`은 서버 장애와 구분되지 않는다)
- `conf.d/blog.conf` (`listen 80`, `server_name _`)
  - `location /api/` → `proxy_pass http://backend:8080;` + `limit_req zone=api ...`
  - `location = /api/v1/auth/login` → 같은 `proxy_pass` + `limit_req zone=login ...` (이 location에는 `api` zone을 겹쳐 걸지 않는다)
  - `location /` → `proxy_pass http://frontend:3000;` + `proxy_buffering off;`. Nginx가 응답을 버퍼링하면 Next의 `Suspense` 스트리밍(11장 '렌더링 전략')이 끝날 때까지 묶여 나간다. Next 문서는 `next.config`에서 `X-Accel-Buffering: no` 헤더를 붙이는 방법도 들지만, 프록시 동작이라 Nginx 설정에 둔다.
  - `/actuator`, `/swagger-ui`, `/v3/api-docs`는 따로 막지 않는다. `/api/`로 시작하지 않아 `location /`(frontend)로 가고 Next의 404가 나간다 (3장).
  - 프록시 헤더는 `server` 블록에 한 번 둔다: `Host $host`, `X-Real-IP $remote_addr`, `X-Forwarded-For $proxy_add_x_forwarded_for`, `X-Forwarded-Proto $scheme`, `X-Request-Id $request_id`(클라이언트가 보낸 값을 덮어씀. 32자 16진수라 백엔드 형식 검사(5장 '로깅')를 통과한다). `proxy_hide_header X-Powered-By;`(Next가 붙이는 프레임워크 정보)도 같이 둔다.
  - 보안 헤더(`server` 블록, `always`로 오류 응답에도): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`. 이 블로그를 다른 사이트가 iframe으로 넣을 일이 없어 `SAMEORIGIN`도 두지 않는다 (Giscus는 우리가 남의 iframe을 넣는 쪽이라 상관없다). CSP `frame-ancestors`는 CSP 전체를 설계할 때 함께 정한다. HSTS는 HTTPS 이슈에서 443 블록에 둔다 (12-3).
  - `proxy_set_header`·`add_header`는 location에서 하나라도 쓰면 `server` 블록 값을 물려받지 않으므로 location에는 두지 않는다.
- backend·frontend가 내려가 있으면 Nginx 기본 `502` HTML이 나간다. 브라우저의 API 호출은 프론트가 `code: null`인 `ApiError`로 처리하고(11장 '에러 처리'), 페이지 요청은 그 HTML이 그대로 보인다. 배포 중 수 초의 다운타임(12-6)뿐이라 별도 오류 페이지는 두지 않는다.
- 429 응답 본문은 Nginx 기본 HTML이다. 프론트는 ProblemDetail이 아닌 응답을 `code: null`로 바꾸고(11장 '에러 처리'), 로그인 화면(11장 '관리자 인증')과 관리자 화면은 이미 429를 "그 외" 행의 "잠시 후 다시 시도해 주세요" 문구로 보여 주므로 프론트를 바꾸지 않는다. 429 전용 문구는 관리자 1명이 겪을 일이 드물어 두지 않는다.

**확인 방법** (로컬, 이슈 완료 조건)
- 실행: `infra/.env`에 12-7의 backend 환경변수(`JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, DB 계정)를 채우고 `IMAGE_TAG=local docker compose -f infra/docker-compose.prod.yml up -d --build`. 이후 compose 명령은 `-p blog-prod`를 붙인다. `docker compose -p blog-prod ps`에서 backend가 `healthy`이고 nginx만 `0.0.0.0:80->80/tcp`를 가진다.
- 로그인: prod 설정 그대로(`Secure` 쿠키) 확인한다. Chromium 계열 브라우저(Chrome·Edge)는 `http://localhost`를 보안 컨텍스트로 보아 `Secure` 쿠키를 HTTP에서도 저장·전송한다 (설계 때 Chromium으로 확인). 그래서 `blog.auth.secure-cookie`를 끄는 로컬용 덮어쓰기를 두지 않는다. Safari 등 다른 브라우저로는 확인하지 않는다.
- 글 목록·상세(`/`, `/posts/{slug}`), 관리자 로그인·글 작성을 브라우저에서 한다.
- 600KB 본문: 관리자 화면 콘솔에서 `content`를 `"가" + "\u0001".repeat(99999)`(JSON에서 약 600KB, 8-4)로 `POST /api/v1/admin/posts`를 `X-XSRF-TOKEN`과 함께 보내 `201`. 확인한 글은 삭제한다.
- 비노출: `curl -i http://localhost/actuator/health`·`/swagger-ui/index.html`·`/v3/api-docs`가 Spring 응답(JSON)이 아니라 Next 404. `nc -z localhost 5432`·`8080`·`3000`이 실패한다 (로컬 개발용 PostgreSQL·`bootRun`·`npm run dev`를 끈 상태에서).
- rate limit: `for i in $(seq 10); do curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost/api/v1/auth/login; done`에서 앞 6회는 `403`(CSRF 없음), 이후 `429`.
- 헤더: `curl -sI http://localhost/`에 `X-Content-Type-Options`·`X-Frame-Options`가 있고 `Server: nginx`(버전 없음), `X-Powered-By`가 없다.
- 요청 ID: `curl -si -H 'X-Request-Id: client-abc' -X POST http://localhost/api/v1/auth/login`(제한이 풀린 뒤)의 응답 `X-Request-Id`가 `client-abc`가 아닌 32자 16진수이고, `docker compose -p blog-prod logs nginx`의 `rid=`와 같다. backend 로그 대조는 브라우저 로그인 한 번으로 한다: 로그인 성공 `INFO`의 `requestId`가 같은 요청의 Nginx `rid=`와 같다.
- 로그 옵션: 각 컨테이너의 `docker inspect -f '{{.HostConfig.LogConfig}}'`가 `json-file`, `max-size:10m`, `max-file:3`.

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
 → SSH 접속 → docker compose pull && docker compose up -d --no-build (12-2)
 → 내부 헬스체크 확인, 실패 시 이전 SHA 태그로 롤백
```
- Flyway 마이그레이션은 backend 기동 시 자동 실행된다. 마이그레이션은 이전 버전 코드와도 호환되게 작성한다 (컬럼 삭제는 코드 배포 이후 별도 마이그레이션으로).
- `compose up -d` 방식은 수 초의 다운타임이 있다. 무중단 배포는 M6 이후 학습 주제로 둔다.

### 12-7. Docker 이미지
운영 배포용 `backend/Dockerfile`, `frontend/Dockerfile`. 빌드 컨텍스트는 각 디렉터리(`docker buildx build --platform linux/arm64 backend`), 플랫폼은 `linux/arm64`(1장). 이미지 이름·태그는 12-2, GHCR push는 12-6 deploy 워크플로가 정한다.

**공통**
- 멀티 스테이지: 빌드 스테이지에서 만들고, 실행 스테이지에는 실행에 필요한 결과만 복사한다.
- 베이스 이미지는 공식 이미지를 **메이저 버전 태그**로 고정한다 (`eclipse-temurin:21-jdk`·`eclipse-temurin:21-jre`, `node:24-alpine`). digest·패치 버전으로 고정하지 않는 이유: 배포마다 이미지를 새로 빌드하므로 메이저 태그면 보안 패치가 자동으로 들어오고, digest 고정은 갱신 도구(Dependabot 등) 없이 1인이 따라가기 어렵다. 대신 같은 커밋이라도 빌드 시점에 따라 베이스가 달라질 수 있다.
- 실행 사용자는 root가 아니다. 베이스 이미지에 있는 일반 사용자(`ubuntu`·`node`, 둘 다 uid 1000)를 `USER`로 지정하고 따로 만들지 않는다.
- 이미지에 비밀값을 넣지 않는다 (`ENV`·`ARG`·복사 파일 모두). 비밀값은 실행 시 compose 환경변수로 받는다 (12-2).
- `.dockerignore`는 각 디렉터리에 두고 빌드 결과물·의존성 폴더·`.env*`·IDE 파일을 뺀다 (로컬 `.env.local` 등이 이미지에 들어가지 않게).

**backend**
- 빌드 스테이지: `eclipse-temurin:21-jdk`에서 저장소의 `./gradlew bootJar`. Gradle 이미지 대신 wrapper를 써서 CI(12-6)와 같은 Gradle 버전으로 빌드한다. `bootJar`만 실행하므로 테스트·`spotlessCheck`는 돌지 않는다 (CI가 한다). 의존성 jar는 레이어가 아니라 BuildKit 캐시 마운트로 재사용한다: `RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon`(빌드 스테이지는 root). 빌드 파일만 먼저 복사해 `./gradlew dependencies`를 돌리는 방식은 그래프(POM)만 해석하고 jar를 받지 않아 쓰지 않는다. 그래서 소스를 따로 나눠 복사하지 않고 한 번에 복사한다.
  - 효과 범위: 같은 머신의 재빌드(로컬 확인)에서만 Gradle 배포판과 jar를 다시 받지 않는다. CI(12-6)는 매번 새 러너라 캐시 마운트가 비어 있어 매번 받는다. 배포는 main 머지 때만 일어나 받는 시간이 문제 되지 않으므로 buildx 캐시 설정은 두지 않는다.
- layered jar를 쓴다: `build/libs/*.jar`(`bootJar`만 실행하면 plain jar는 생기지 않아 하나뿐)를 `application.jar`로 복사하고 `java -Djarmode=tools -jar application.jar extract --layers --destination extracted`로 풀고, 실행 스테이지에 `dependencies` → `spring-boot-loader` → `snapshot-dependencies` → `application` 순으로 각각 `COPY`한다 (Spring Boot 공식 Dockerfile 방식). 코드만 바뀐 배포는 마지막 레이어만 바뀌어, 작은 서버의 `docker compose pull` 양이 준다.
- 실행 스테이지: `eclipse-temurin:21-jre`, `EXPOSE 8080`, `ENTRYPOINT ["java", "-jar", "application.jar"]`. JRE 이미지에 `curl`이 있어 compose healthcheck(12-2)에 쓸 수 있다.
- 기본 프로필은 **이미지**에 `ENV SPRING_PROFILES_ACTIVE=prod`로 둔다. 이 이미지는 운영 전용이고(로컬은 IDE 실행, 12-1), compose에서 빠뜨렸을 때 Swagger가 켜지고 관리자·JWT 키 검사가 꺼진 기본 설정으로 뜨지 않게 한다. 필요하면 compose가 덮어쓴다.
- JVM 옵션은 이미지에 넣지 않고 compose의 `JAVA_TOOL_OPTIONS`로 준다. 메모리 같은 값은 서버 사양에 따라 달라서다. 주지 않으면 JVM 기본값(컨테이너 메모리 인식)으로 뜬다.
- 실행 시 필요한 환경변수: `DB_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`(`application-prod.yaml`), `JWT_SECRET`(10-6), `ADMIN_USERNAME`, `ADMIN_PASSWORD`(5장 '관리자 계정 생성'). 관리자·JWT 값이 비면 prod 설정에 따라 기동이 멈추고 컨테이너는 0이 아닌 코드로 종료한다.

**frontend**
- Node 버전은 `frontend/.nvmrc`(현재 `24`)와 같은 메이저 태그 `node:24-alpine`. Dockerfile은 `.nvmrc`를 읽지 못하므로 `.nvmrc`를 바꿀 때 Dockerfile의 태그도 같이 바꾼다.
- 스테이지: 의존성(`package.json`·`package-lock.json`만 복사 후 `npm ci`) → 빌드(`npm run build`) → 실행. 빌드에는 `API_INTERNAL_URL`도 백엔드도 필요 없다 (11장 '빌드 시 API 의존 제거'·'API 주소 처리').
- 실행 스테이지에는 `.next/standalone`(→ `./`), `.next/static`(→ `./.next/static`), `public`(→ `./public`)만 복사한다. standalone 서버가 `.next/cache`에 쓰므로 `--chown=node:node`로 복사한다.
  - `public`은 지금 비어 있어 git에 없다. CI 체크아웃에도 폴더가 있도록 `frontend/public/.gitkeep`을 커밋한다.
- `ENV NODE_ENV=production`, `PORT=3000`, `HOSTNAME=0.0.0.0`, `EXPOSE 3000`, `CMD ["node", "server.js"]`. `HOSTNAME`을 주지 않으면 Docker가 넣는 컨테이너 ID 호스트명에만 바인딩되어 다른 컨테이너(Nginx)에서 접속하지 못한다.
- `API_INTERNAL_URL`은 실행 시 compose `environment`로 준다 (11장 'API 주소 처리').

**확인 방법** (로컬, 이슈 완료 조건)
- 빌드: `docker buildx build --platform linux/arm64 -t blog-backend:local --load backend`, frontend도 같다. 로컬 PostgreSQL·백엔드를 끈 상태에서 성공해야 한다.
- backend: `infra/docker-compose.yml`의 PostgreSQL을 띄우고 `DB_URL=jdbc:postgresql://host.docker.internal:5432/blog`와 DB 계정, `JWT_SECRET`(`openssl rand -base64 32`), `ADMIN_USERNAME`·`ADMIN_PASSWORD`로 실행 → `curl localhost:8080/actuator/health`가 `UP`. 세 값 중 하나라도 빼면 컨테이너가 종료된다(`docker ps -a`의 Exited, 0이 아닌 코드). 관리자 값 검사는 `admin` 행이 없을 때만 하므로(5장 '관리자 계정 생성'), `ADMIN_*` 누락은 `docker compose down -v`로 비운 DB에서 성공 실행보다 먼저 확인한다.
- frontend: 시드 데이터가 있는 백엔드(`local` 프로필 `bootRun`)를 띄우고 `API_INTERNAL_URL=http://host.docker.internal:8080`만 주어 실행 → `curl localhost:3000/`의 글 목록과 `/posts/{slug}` 상세가 표시된다.
- 실행 사용자: `docker exec <컨테이너> id -u`가 두 이미지 모두 `0`이 아니다.

---
