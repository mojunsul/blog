## 12. 인프라와 배포

### 12-1. 로컬 개발
- `infra/docker-compose.yml`로 PostgreSQL만 실행
- 시드 데이터는 `local` 프로필로 처음 기동할 때 들어간다. 다시 넣으려면 `docker compose down -v`로 DB 볼륨을 지운 뒤 재기동한다.
- Spring Boot(IDE, `local` 프로필), Next.js(`npm run dev`)는 직접 실행

### 12-2. 서버 (OCI)
- `docker-compose.prod.yml`: nginx, certbot, frontend, backend, postgres, backup (certbot은 12-3, backup은 12-5. 둘 다 `profiles`로 상주하지 않는다)
  - 최상위 `name: blog-prod`. 로컬용 `docker-compose.yml`과 같은 `infra/`에 있어 이름을 주지 않으면 둘 다 프로젝트 이름이 `infra`가 되고, 볼륨(`infra_postgres-data`)을 함께 써 로컬 시드 DB로 운영 구성이 뜬다.
  - 이미지: `image: ghcr.io/mojunsul/blog-backend:${IMAGE_TAG:-latest}`(frontend는 `blog-frontend`)와 `build: ../backend`(`../frontend`)를 함께 둔다. 로컬은 `IMAGE_TAG=local`로 `up -d --build`, 서버는 `pull` 후 `up -d --no-build`(소스가 없어도 `build`는 빌드할 때만 읽힌다). deploy(12-6)도 이 이름으로 push한다. deploy 전의 첫 배포는 아래 '서버 준비'대로 서버에서 빌드한다.
  - 호스트에 여는 포트는 nginx `80:80`·`443:443`뿐이다. postgres·backend·frontend에는 `ports`를 두지 않는다 (같은 compose 네트워크에서 서비스 이름으로 접속).
  - postgres: `postgres:18`(로컬과 같다), `TZ: UTC`, named volume `postgres-data:/var/lib/postgresql`, `pg_isready` healthcheck(로컬과 같다)
    - `POSTGRES_DB: ${POSTGRES_DB:-blog}`, `POSTGRES_USER: ${POSTGRES_USER:-blog}` (로컬 compose와 같은 기본값)
    - `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}`. 비밀번호만 기본값 없이 필수로 둔다. 비거나 없으면 compose가 컨테이너를 만들기 전에 실패해, 로컬 compose의 기본값 `blog`로 운영 DB가 뜨지 않는다.
  - backend: 12-7의 환경변수를 `env_file`이 아니라 `environment`에 `${VAR}` 치환으로 넣는다 (값은 `infra/.env`). postgres healthy 이후 기동.
    - DB 값은 postgres와 같은 식을 쓴다: `DB_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-blog}`, `POSTGRES_USER: ${POSTGRES_USER:-blog}`, `POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?...}`. `infra/.env`에서 `POSTGRES_DB`·`POSTGRES_USER`를 비워도 두 서비스가 같은 기본값을 받는다 (`env_file`이면 빈 값이 backend에만 그대로 들어가 어긋난다).
    - `JWT_SECRET`·`ADMIN_USERNAME`·`ADMIN_PASSWORD`는 기본값 없이 `${VAR}`. 비면 빈 문자열이 들어가 12-7대로 backend 기동이 멈춘다.
  - frontend: `API_INTERNAL_URL: http://backend:8080` (11장 'API 주소 처리')
- PostgreSQL 데이터는 named volume으로 영속화
- 비밀값은 서버의 `infra/.env`에만 둔다. 원본은 GitHub Secrets이고, 자동 배포부터는 deploy가 매번 `.env`를 다시 쓴다 (12-6 '`.env` 동기화')
  - `POSTGRES_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` (Object Storage는 인스턴스 주체로 인증해 키가 없다, 12-5)
  - 비밀값이 아닌 `IMAGE_TAG`(비우면 `latest`)도 `infra/.env.example`에 둔다. 로컬은 셸에서 `IMAGE_TAG=local`로 덮어쓴다. 서버 첫 배포(deploy 이슈 전)에는 `infra/.env`에 `IMAGE_TAG=local`을 둔다는 안내(아래 '서버 준비')도 같은 주석에 적는다.
  - `infra/.env.example`의 기본값 안내 주석은 로컬 compose 기준이므로, 운영 compose는 `POSTGRES_PASSWORD`가 필수라는 점을 함께 적는다.
  - `JWT_SECRET`은 `openssl rand -base64 32`로 만든다 (Base64, 디코딩 후 32바이트 이상, 10-6). 바꾸면 발급된 Access 토큰이 모두 무효가 된다.
  - 관리자 비밀번호 변경 (5장 '관리자 계정 생성', README에 같은 절차를 적는다). 저장소 폴더(`~/blog`)에서:
    1. GitHub Secret `ADMIN_PASSWORD`를 바꾼다 (서버 `.env`는 deploy가 덮어쓰므로 직접 고치지 않는다, 12-6).
    2. `docker compose -f infra/docker-compose.prod.yml exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DELETE FROM admin"'` (`refresh_token`도 함께 지워져 로그인 상태가 풀린다. 이미 발급된 Access 토큰은 만료(15분)까지 남는다)
    3. Actions에서 deploy를 `workflow_dispatch`로 실행한다. `.env`가 바뀌어 backend 컨테이너가 다시 만들어진다 (`restart`는 컨테이너를 다시 만들지 않아 바뀐 값을 읽지 않는다).
    4. 새 비밀번호로 로그인한다. backend가 기동하지 않으면 `logs backend`에서 값 검사(5장) 오류를 보고 Secret을 고쳐 3을 다시 한다. 기동하지 못하면 deploy가 이전 `.env`로 롤백하므로 이전 비밀번호로 계정이 다시 만들어진다.
- **서버 준비** (최초 1회, 절차는 README)
  - 인스턴스: `VM.Standard.A1.Flex`(arm64, Always Free) 4 OCPU·24GB 한 대, Canonical Ubuntu 24.04, 부트 볼륨 기본값. 예약 공인 IP(Reserved Public IP)를 붙인다 (인스턴스를 다시 만들어도 DNS를 고치지 않게).
  - OCI 보안 목록: 수신 TCP 80·443·22 모두 `0.0.0.0/0`. 22는 deploy(12-6)의 GitHub 러너 IP가 고정되지 않아 열고, 키 인증만 허용한다 (`/etc/ssh/sshd_config`의 `PasswordAuthentication no`를 확인).
  - 인스턴스 iptables: Ubuntu 이미지 기본 규칙이 22 외 수신을 `REJECT`하므로 `/etc/iptables/rules.v4`에서 22 규칙 다음 줄에 80·443 `ACCEPT`를 넣고 `iptables-restore`로 적용한다. Docker 설치 전에 한다 (`iptables-restore`는 기존 규칙을 비우고 다시 쓰므로 Docker가 만든 규칙이 지워진다).
  - Docker: 공식 문서의 apt 저장소 방식으로 `docker-ce`·`docker-compose-plugin`을 설치하고 `ubuntu` 사용자를 `docker` 그룹에 넣는다 (`get.docker.com` 스크립트는 문서가 운영용으로 권하지 않는다).
  - 저장소: `git clone https://github.com/mojunsul/blog.git ~/blog` (Public이라 인증이 필요 없다). compose·Nginx 설정은 이 폴더에서 읽는다. 설정 갱신은 deploy(12-6)가 배포 커밋을 체크아웃해 맞춘다 (서버에서 `git pull`하지 않는다).
    - 이 이슈의 설정(certbot 서비스, 443 블록, `.gitignore`)은 머지 전 구현 검증에서 서버로 확인해야 하므로, 최초 발급·첫 배포·확인 방법(12-3)은 기능 브랜치에서 한다: clone 뒤 `git checkout chore/#109-server-https`, 구현 세션이 push하면 `git pull`. PR이 머지되면 돌아가기 전에 브랜치 끝 SHA를 적어 두고(`git rev-parse HEAD`) `git checkout main && git pull`로 돌아간다. merge commit으로 머지하므로 main의 커밋은 브랜치와 다르고, 그사이 main에 다른 `infra/` 변경이 머지됐을 수 있다. `git diff <브랜치 끝 SHA> HEAD -- infra`가 비어 있으면 설정이 같으므로 컨테이너를 다시 띄우지 않고, 차이가 있으면 `docker compose -f infra/docker-compose.prod.yml up -d --force-recreate`로 모든 서비스를 다시 만든다. Nginx 설정(`nginx.conf`·`conf.d/`, 12-4)은 마운트만 되어 있어 compose 정의가 같으면 `up -d`가 컨테이너를 다시 만들지 않고, 파일 하나를 마운트한 `nginx.conf`는 git이 파일을 교체하면 reload로도 반영되지 않기 때문이다. nginx로 좁히지 않는 이유: 같은 차이에 다른 서비스의 마운트 파일이 섞여도 놓치지 않고, 한 번뿐인 절차라 수 초 다운타임은 문제되지 않는다 (`infra/.env`·`infra/certbot/`은 git이 추적하지 않아 브랜치를 바꿔도 남는다).
  - `.env`: `infra/.env.example`을 `infra/.env`로 복사해 채우고 `chmod 600`. 같은 값을 GitHub Secrets(12-6 표)에도 등록한다. 첫 자동 배포부터 deploy가 이 파일을 다시 쓴다.
  - 첫 배포 이미지는 서버에서 빌드한다: `infra/.env`에 `IMAGE_TAG=local`을 두고 `docker compose -f infra/docker-compose.prod.yml up -d --build`. 서버도 arm64라 그대로 빌드된다. 로컬에서 빌드해 GHCR에 수동 push하는 방식은 쓰기 토큰(PAT)을 따로 만들어 관리해야 해 택하지 않았다. `IMAGE_TAG`를 셸이 아니라 `.env`에 두는 이유: 이후 `up`·`run`에서 빠지면 `latest`를 GHCR에서 받으려다 실패한다. 첫 자동 배포부터 deploy가 커밋 SHA로 바꾼다 (12-6).
  - 순서: 서버 준비 → DNS(12-3) → `.env` → 인증서 최초 발급(12-3) → 첫 배포 → 갱신 cron(12-3). 인증서가 없으면 nginx가 기동하지 못하므로 발급이 배포보다 먼저다. 기존 서버를 새 서버로 옮길 때(복구)는 첫 배포 대신 12-5 '복구'의 운영 DB 복구를 하고, 그 마지막 단계에서 백업 crontab을 다시 등록한다.
- 모든 상주 서비스에 `restart: unless-stopped` (실행할 때만 뜨는 certbot은 제외, 12-3)
- backend에 `healthcheck` 설정 (`/actuator/health`), nginx·frontend는 backend healthy 이후 기동 (`depends_on: condition: service_healthy`). nginx와 frontend 사이에는 순서를 두지 않는다: nginx는 업스트림 이름을 요청 때 확인하므로(12-4 '업스트림 이름 확인') frontend가 아직 없어도 기동하고, 그동안 `/`는 `502`다.
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
**도메인·DNS**
- 도메인은 Cloudflare Registrar에서 산다 (`.com` 등, `.kr`은 팔지 않는다). 원가로 팔아 연장 가격이 오르지 않고, 등록과 함께 DNS가 Cloudflare로 정해져 1장과 맞는다. 실제 이름은 저장소 어디에도(설정 파일·README·문서) 두지 않는다 (아래).
- Cloudflare DNS(1장)에 A 레코드 `blog` → 예약 공인 IP(12-2). 프록시 상태는 **DNS only**(회색 구름)로 둔다. 프록시를 켜면 Cloudflare가 TLS를 끝내고 Nginx의 `$remote_addr`가 Cloudflare IP가 되어, IP당 rate limit(12-4)이 방문자를 한 묶음으로 센다.
- 저장소의 설정 파일에는 도메인을 쓰지 않는다. `server_name _`이고 인증서는 `--cert-name blog`로 발급해 경로가 `/etc/letsencrypt/live/blog/`로 고정된다. README·문서의 명령에는 자리표시자 `blog.도메인`·`<이메일>`만 쓰고, 실제 값은 서버에서 명령을 실행할 때만 입력한다. 실제 도메인이 들어가는 곳은 Cloudflare DNS와 서버에 남는 인증서 설정(`infra/certbot/conf`, git 제외)뿐이다.

**compose** (12-2)
- `certbot`: `certbot/certbot:v5.8.0`. 이 이미지는 메이저 태그가 없고 `latest`는 메이저가 바뀌어도 따라가므로 정확한 버전으로 고정한다 (12-7 메이저 태그 원칙의 예외). `profiles: [certbot]`이고 `restart`를 두지 않는다: 상주하지 않고 `docker compose -f infra/docker-compose.prod.yml run --rm certbot <명령>`으로만 실행한다 (배포의 `up -d`는 띄우지 않는다). 로그 옵션은 다른 서비스와 같다.
- 인증서와 ACME 웹루트는 호스트 폴더를 bind mount 한다: `./certbot/conf:/etc/letsencrypt`, `./certbot/www:/var/www/certbot` (nginx는 둘 다 `:ro`). named volume이 아닌 이유: 최초 발급 때 compose 밖의 임시 Nginx가 같은 웹루트를 읽어야 하고, 로컬에서 자체 서명 인증서를 같은 경로에 넣기 쉽다. `infra/certbot/`은 개인 키가 있어 `.gitignore`에 넣는다.
- nginx: `443:443`을 열고 위 두 폴더를 마운트한다.

**Nginx** (`conf.d/blog.conf`, 12-4)
- 80 server: `location /.well-known/acme-challenge/ { root /var/www/certbot; }`, 그 외는 `location / { return 301 https://$host$request_uri; }`.
- 443 server: `listen 443 ssl;`, `http2 on;`, `ssl_certificate /etc/letsencrypt/live/blog/fullchain.pem;`, `ssl_certificate_key /etc/letsencrypt/live/blog/privkey.pem;`. 프로토콜·암호 목록은 Nginx 기본값(TLSv1.2·TLSv1.3)을 쓴다. 12-4의 프록시 헤더·보안 헤더·location은 모두 이 블록에 둔다.
- HSTS: 443 server에 `add_header Strict-Transport-Security "max-age=31536000" always;`. `includeSubDomains`·`preload`는 두지 않는다: `blog.`의 하위 도메인은 쓰지 않고, preload 목록은 등록 후 빼기 어렵다.
- 인증서 파일이 없으면 nginx가 기동하지 못하고 재시작을 반복한다. 그래서 최초 발급은 compose를 띄우기 전에 아래 절차로 한다.

**최초 발급** (서버, 저장소 폴더에서 1회)
1. `dig +short blog.도메인`이 예약 공인 IP를 돌려주는지 확인한다.
2. 임시 Nginx로 웹루트를 연다: `docker run -d --rm --name acme -p 80:80 -v "$PWD/infra/certbot/www:/usr/share/nginx/html:ro" nginx:1-alpine`. 이미지 기본 설정이 80에서 `/usr/share/nginx/html`을 서빙하므로 설정 파일이 따로 필요 없다.
3. `docker compose -f infra/docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot --cert-name blog -d blog.도메인 -m <이메일> --agree-tos --no-eff-email --dry-run`이 성공하면 `--dry-run`을 빼고 다시 실행한다. 이메일은 저장소에 넣지 않고 명령에만 쓴다. 실패하면 오류(DNS·80 포트)를 고친 뒤 `--dry-run`부터 다시 한다. `--dry-run`은 스테이징 서버를 써서 반복 실패해도 실제 발급 제한에 걸리지 않는다.
4. `docker stop acme` 후 첫 배포(12-2 '서버 준비')를 한다.
- 웹루트로 발급하는 이유: `--standalone`은 임시 Nginx 없이 한 줄이지만 갱신 설정에 standalone이 저장돼, nginx가 80을 쓰는 동안 `certbot renew`가 실패한다. 더미(자체 서명) 인증서를 먼저 넣어 nginx를 띄운 뒤 지우고 발급하는 방식은 단계가 더 많다.

**갱신** (호스트 cron)
- `ubuntu` 사용자 crontab에 한 줄을 둔다 (README에 같은 줄을 적는다):
  ```
  17 3 * * * cd $HOME/blog && { docker compose -f infra/docker-compose.prod.yml run --rm -T certbot renew -q && docker compose -f infra/docker-compose.prod.yml exec -T nginx nginx -s reload; } >> $HOME/certbot-renew.log 2>&1
  ```
  - `certbot renew`는 수명의 약 1/3이 남은 인증서만 갱신하므로(certbot 4.0+ 기본값, 90일 인증서는 30일·64일 인증서는 약 21일. Let's Encrypt가 ARI로 더 이른 시점을 알려 주면 그때 갱신한다) 매일 돌려도 대부분 아무것도 하지 않는다. reload는 갱신 여부와 상관없이 매일 한다 (graceful이라 연결이 끊기지 않는다). `--deploy-hook`은 certbot 컨테이너 안에서 실행돼 nginx 컨테이너에 신호를 보낼 수 없다.
  - `-T`: cron에는 TTY가 없어 `run`·`exec`가 TTY를 붙이려다 실패하지 않게 한다.
- certbot 컨테이너 루프(상주하며 12시간마다 `renew`) 대신 cron인 이유: 루프 방식은 reload를 위해 nginx의 `command`도 셸 루프로 덮어써야 해 이미지 기본 진입 스크립트를 건너뛰고, 두 루프의 주기가 따로 돈다. cron은 명령이 한 줄로 드러나고 갱신 직후 reload된다.
- 실패 처리: `renew`가 실패하면 reload를 건너뛰고 오류가 `~/certbot-renew.log`에 남는다. 다음 날 다시 시도하며, 수명의 1/3이 남은 때부터 시도하므로 그 안에 고치면 된다. Let's Encrypt는 만료 안내 메일을 보내지 않으므로 만료 감시는 M5 Uptime Kuma의 인증서 만료 알림으로 한다(13장 M5). M5 전까지는 매주 월요일 `docker compose -f infra/docker-compose.prod.yml run --rm certbot certificates`로 남은 기간(`VALID: N days`)을 본다. 갱신이 정상이면 남은 기간이 수명의 1/3 아래로 내려가지 않으므로, 1/3 미만(90일 인증서는 30일, 64일 인증서는 21일 미만)이면 `~/certbot-renew.log`를 보고 고친다. 수명은 `certbot certificates`에 나오지 않으므로 `sudo openssl x509 -noout -startdate -enddate -in infra/certbot/conf/live/blog/cert.pem`의 두 날짜 차이로 본다 (Let's Encrypt는 2027-02-10부터 기본 64일로 발급한다). README의 인증서 갱신 절차에 이 주간 점검 명령과 기준을 함께 적는다. 주 1회인 이유: 갱신 실패가 시작된 뒤 늦어도 7일 안에 알아채, 그때도 90일 인증서는 23일·64일 인증서는 14일 이상 남는다. `-q`라 성공하면 certbot은 출력이 없어 로그가 거의 늘지 않으므로 따로 정리하지 않는다.

**로컬에서 운영 compose 띄우기**
- 443 블록 때문에 인증서가 있어야 nginx가 뜬다. 자체 서명 인증서를 같은 경로에 만든다: `mkdir -p infra/certbot/conf/live/blog && openssl req -x509 -nodes -newkey rsa:2048 -days 30 -subj /CN=localhost -keyout infra/certbot/conf/live/blog/privkey.pem -out infra/certbot/conf/live/blog/fullchain.pem`
- 이후 12-4 '확인 방법'의 `http://localhost`는 `https://localhost`(`curl -k`, 브라우저는 경고 화면에서 계속 진행)로 바꿔 읽는다. `http://localhost`는 `301`이다.

**확인 방법** (서버, 이슈 완료 조건. `blog.도메인`은 실제 도메인)
- 브라우저로 `https://blog.도메인`에서 관리자 로그인 → 글 작성(`PUBLISHED`) → 글 목록·상세를 확인한다.
- `curl -sI http://blog.도메인/posts`가 `301`이고 `Location: https://blog.도메인/posts`. `curl -sI https://blog.도메인/`에 `Strict-Transport-Security: max-age=31536000`이 있다.
- `docker compose -f infra/docker-compose.prod.yml run --rm certbot renew --dry-run`이 성공한다. crontab 줄의 명령을 셸에서 한 번 직접 실행해 종료 코드 0과 `certbot-renew.log`를 확인한다.
- 서버 밖(로컬 PC)에서 `nc -z -w 3 <공인 IP> 5432`·`8080`·`3000`이 실패하고, `curl -s -o /dev/null -w '%{http_code}\n' https://blog.도메인/actuator/health`가 `404`(Next)다.
- 관리자 비밀번호 변경: README 절차(12-2)대로 바꾼 뒤 새 비밀번호로 로그인되고 이전 비밀번호는 `401`이다.

### 12-4. Nginx 설정 요점
nginx 이미지는 `nginx:1-alpine`(12-7과 같은 메이저 태그 원칙). `infra/nginx/nginx.conf`를 `/etc/nginx/nginx.conf`로, `infra/nginx/conf.d/`를 `/etc/nginx/conf.d/`로 읽기 전용 마운트한다. 접근·오류 로그는 이미지 기본대로 표준 출력·표준 오류로 나가 Docker 로그 제한(12-2)을 받는다.

- `nginx.conf` (`http` 블록)
  - `server_tokens off;` (`Server: nginx`만 남는다. 업스트림의 `Server` 헤더는 Nginx가 기본으로 숨긴다)
  - 접근 로그 형식: 기본 `combined` 뒤에 `rt=$request_time rid=$request_id`를 붙인 `main`
  - gzip: `gzip on`, `gzip_types`에 `application/json`·`text/css`·`application/javascript`·`image/svg+xml`, `gzip_min_length 1024`. 주 대상은 `/api` JSON이다 (Spring은 압축하지 않는다). Next 응답은 Next가 직접 압축해 `Content-Encoding`이 있어 Nginx가 다시 압축하지 않는다.
  - `client_max_body_size 2m;` — 글 작성·수정 요청 본문은 `content` 최대 100,000자 기준 약 600KB 이하다 (8-4). 기본값 1MB를 그대로 쓰지 않고 여유를 두어 명시한다.
  - 업스트림 이름 확인: `resolver 127.0.0.11 valid=10s;`(Docker 내장 DNS). `proxy_pass`에 호스트 이름을 그대로 쓰면 오픈소스 Nginx는 설정을 읽을 때 한 번만 확인해, frontend보다 먼저 뜨면 `host not found in upstream`으로 종료되고, 배포(12-6)로 backend·frontend 컨테이너만 다시 만들어 IP가 바뀌면 옛 IP로 보내 `502`가 이어진다. 그래서 `conf.d/blog.conf`의 `proxy_pass`는 변수로 써서 요청 때 확인하고 결과를 10초 캐시한다. `upstream` 블록의 `server ... resolve`(1.27.3 이상)는 메이저 태그(`1-alpine`)로는 버전을 보장하기 어렵고 국내 자료도 적고, 배포마다 nginx를 재시작하는 방식은 배포 절차가 늘고 기동 순서 문제가 남아 둘 다 택하지 않았다. 이름을 확인하지 못하면(컨테이너가 없을 때) `502`다.
  - rate limit zone (키 `$binary_remote_addr`, IP당)
    | zone | 대상 | `rate` | `burst` |
    |---|---|---|---|
    | `login` (1m) | `/api/v1/auth/login` | `5r/m` | `5 nodelay` |
    | `api` (10m) | 그 외 `/api/` | `10r/s` | `20 nodelay` |
    - 로그인: 연속 6회(1 + burst)까지 바로 처리하고, 그 뒤는 12초에 1회. 관리자 1명이 비밀번호를 몇 번 틀리는 정도는 막히지 않고, 대입 공격은 분당 5회로 줄어든다.
    - `api`: 서버 컴포넌트의 API 호출은 Nginx를 거치지 않으므로(`http://backend:8080`, 3장) 브라우저가 직접 부르는 API(관리자 화면, M6 조회수)만 받는다. 관리자 화면 한 번에 몇 건이라 여유가 크다. 조회수 zone은 M6에서 추가한다.
    - `limit_req_status 429;` (기본값 `503`은 서버 장애와 구분되지 않는다)
- `conf.d/blog.conf` (`server_name _`): 80 server는 ACME 경로와 HTTPS 리다이렉트만 두고(12-3), 아래 항목은 모두 443 server에 둔다.
  - `server` 블록에 `set $backend backend:8080;`, `set $frontend frontend:3000;`을 두고 `proxy_pass http://$backend;`처럼 쓴다. URI 부분(끝의 `/`)을 붙이지 않으므로 변수를 써도 요청 URI가 그대로 전달된다 (`/api/...` 경로 유지, 3장).
  - `location /api/` → `proxy_pass http://$backend;` + `limit_req zone=api ...`
  - `location = /api/v1/auth/login` → 같은 `proxy_pass` + `limit_req zone=login ...` (이 location에는 `api` zone을 겹쳐 걸지 않는다)
  - `location /` → `proxy_pass http://$frontend;` + `proxy_buffering off;`. Nginx가 응답을 버퍼링하면 Next의 `Suspense` 스트리밍(11장 '렌더링 전략')이 끝날 때까지 묶여 나간다. Next 문서는 `next.config`에서 `X-Accel-Buffering: no` 헤더를 붙이는 방법도 들지만, 프록시 동작이라 Nginx 설정에 둔다.
  - `/actuator`, `/swagger-ui`, `/v3/api-docs`는 따로 막지 않는다. `/api/`로 시작하지 않아 `location /`(frontend)로 가고 Next의 404가 나간다 (3장).
  - 프록시 헤더는 `server` 블록에 한 번 둔다: `Host $host`, `X-Real-IP $remote_addr`, `X-Forwarded-For $proxy_add_x_forwarded_for`, `X-Forwarded-Proto $scheme`, `X-Request-Id $request_id`(클라이언트가 보낸 값을 덮어씀. 32자 16진수라 백엔드 형식 검사(5장 '로깅')를 통과한다). `proxy_hide_header X-Powered-By;`(Next가 붙이는 프레임워크 정보)도 같이 둔다.
  - 보안 헤더(`server` 블록, `always`로 오류 응답에도): `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`. 이 블로그를 다른 사이트가 iframe으로 넣을 일이 없어 `SAMEORIGIN`도 두지 않는다 (Giscus는 우리가 남의 iframe을 넣는 쪽이라 상관없다). CSP `frame-ancestors`는 CSP 전체를 설계할 때 함께 정한다. HSTS도 여기에 같이 둔다 (값은 12-3).
  - `proxy_set_header`·`add_header`는 location에서 하나라도 쓰면 `server` 블록 값을 물려받지 않으므로 location에는 두지 않는다.
- backend·frontend가 내려가 있으면 Nginx 기본 `502` HTML이 나간다. 브라우저의 API 호출은 프론트가 `code: null`인 `ApiError`로 처리하고(11장 '에러 처리'), 페이지 요청은 그 HTML이 그대로 보인다. 배포 중 수 초의 다운타임(12-6)뿐이라 별도 오류 페이지는 두지 않는다.
- 429 응답 본문은 Nginx 기본 HTML이다. 프론트는 ProblemDetail이 아닌 응답을 `code: null`로 바꾸고(11장 '에러 처리'), 로그인 화면(11장 '관리자 인증')과 관리자 화면은 이미 429를 각 화면 결과 표의 "그 외" 행 문구로 보여 주므로 프론트를 바꾸지 않는다. 429 전용 문구는 관리자 1명이 겪을 일이 드물어 두지 않는다.

**확인 방법** (로컬, 이슈 완료 조건. HTTPS(12-3) 추가 후에는 12-3 '로컬에서 운영 compose 띄우기'를 먼저 한다)
- 실행: `infra/.env`에 12-7의 backend 환경변수(`JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, DB 계정)를 채우고 `IMAGE_TAG=local docker compose -f infra/docker-compose.prod.yml up -d --build`. 조회 명령(`ps`·`logs`)은 파일 없이 `-p blog-prod`로 하고, 컨테이너를 만드는 명령(`up` 등)은 `-p`만으로는 파일을 찾지 못해 위와 같이 `IMAGE_TAG=local`과 `-f infra/docker-compose.prod.yml`을 붙인다. `docker compose -p blog-prod ps`에서 backend가 `healthy`이고 nginx만 `0.0.0.0:80->80/tcp`·`0.0.0.0:443->443/tcp`를 가진다.
- 로그인: prod 설정 그대로(`Secure` 쿠키) 확인한다. Chromium 계열 브라우저(Chrome·Edge)는 `http://localhost`를 보안 컨텍스트로 보아 `Secure` 쿠키를 HTTP에서도 저장·전송한다 (설계 때 Chromium으로 확인). 그래서 `blog.auth.secure-cookie`를 끄는 로컬용 덮어쓰기를 두지 않는다. Safari 등 다른 브라우저로는 확인하지 않는다.
- 브라우저에서 관리자 로그인 → 글 작성(`PUBLISHED`) → 글 목록·상세(`/`, `/posts/{slug}`) 순으로 확인한다. 운영 DB에는 시드가 없어 처음에는 목록이 비어 있다.
- 필수 비밀번호: `infra/.env`의 `POSTGRES_PASSWORD`를 비우고 `docker compose -f infra/docker-compose.prod.yml config`가 `POSTGRES_PASSWORD is required`로 실패한다.
- 재생성 후 연결: `IMAGE_TAG=local docker compose -f infra/docker-compose.prod.yml up -d --force-recreate --no-deps backend frontend` 뒤 backend가 `healthy`가 되면 `curl -s -o /dev/null -w '%{http_code}\n' http://localhost/`와 `/api/v1/posts`가 `502`가 아니라 `200`이다 (nginx는 그대로 둔 채).
- 600KB 본문: 관리자 화면 콘솔에서 `content`를 `"가" + "\u0001".repeat(99999)`(JSON에서 약 600KB, 8-4)로 `POST /api/v1/admin/posts`를 `X-XSRF-TOKEN`과 함께 보내 `201`. 확인한 글은 삭제한다.
- 비노출: `curl -i http://localhost/actuator/health`·`/swagger-ui/index.html`·`/v3/api-docs`가 Spring 응답(JSON)이 아니라 Next 404. `nc -z localhost 5432`·`8080`·`3000`이 실패한다 (로컬 개발용 PostgreSQL·`bootRun`·`npm run dev`를 끈 상태에서).
- rate limit: `for i in $(seq 10); do curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost/api/v1/auth/login; done`에서 앞 6회는 `403`(CSRF 없음), 이후 `429`.
- 헤더: `curl -sI http://localhost/`에 `X-Content-Type-Options`·`X-Frame-Options`가 있고 `Server: nginx`(버전 없음), `X-Powered-By`가 없다.
- 요청 ID: `curl -si -H 'X-Request-Id: client-abc' -X POST http://localhost/api/v1/auth/login`(제한이 풀린 뒤)의 응답 `X-Request-Id`가 `client-abc`가 아닌 32자 16진수이고, `docker compose -p blog-prod logs nginx`의 `rid=`와 같다. backend 로그 대조는 브라우저 로그인 한 번으로 한다: 로그인 성공 `INFO`의 `requestId`가 같은 요청의 Nginx `rid=`와 같다.
- 로그 옵션: 각 컨테이너의 `docker inspect -f '{{.HostConfig.LogConfig}}'`가 `json-file`, `max-size:10m`, `max-file:3`.

### 12-5. DB 백업
호스트 cron이 매일 `infra/backup/backup.sh`를 실행한다: postgres 컨테이너의 `pg_dump`(custom 형식) → 호스트 임시 파일 → compose `backup` 서비스(OCI CLI)가 Object Storage 버킷 `blog-backup`에 업로드. 보관은 버킷의 수명 주기 규칙(14일)이 한다. **복구 절차를 README에 적고, 최소 한 번은 실제로 복구 테스트를 한다.**

**결정**
- 호스트 cron: 인증서 갱신(12-3)과 같은 방식이라 명령이 crontab 한 줄로 드러난다. backup 컨테이너 안에서 cron을 돌리면 cron 데몬이 든 이미지를 따로 만들어야 한다. `pg_dump`는 postgres 컨테이너에서 `exec`로 실행해, 클라이언트 버전이 항상 서버와 같다 (`pg_dump`는 서버보다 낮은 버전이면 덤프를 거부한다).
- 덤프 형식은 custom(`-Fc`): 기본으로 압축되어 gzip 단계가 필요 없고, `pg_restore`의 `--no-owner`·`--exit-on-error`·테이블 단위 복구를 쓸 수 있다. plain SQL + gzip은 읽기 쉽지만 `psql`이 오류 뒤에도 계속 진행해(`ON_ERROR_STOP`을 따로 줘야 한다) 택하지 않았다.
- 업로드는 OCI CLI + **인스턴스 주체**(instance principal): 서버 인스턴스 자체가 권한을 받아, 저장하는 키가 없다 (AWS EC2의 IAM 역할과 같은 방식). S3 호환 API는 `aws` CLI로 익숙하지만 사용자에 묶인 고정 키(Customer Secret Key)를 `.env`·GitHub Secrets·deploy 동기화(12-6)로 관리해야 해 택하지 않았다. 그래서 `infra/.env.example`에 백업 값을 추가하지 않는다. 네임스페이스는 CLI가 요청 때 조회하고 리전은 인스턴스 리전을 쓴다.
- 서버 권한은 객체 생성·조회·읽기만 준다 (삭제·덮어쓰기 없음). 서버가 털려도 지난 백업을 지우거나 덮어쓸 수 없고, 정리는 Object Storage 서비스의 수명 주기 규칙이 한다. 그래서 스크립트는 보관 정리를 하지 않는다.

**compose `backup` 서비스** (`docker-compose.prod.yml`, certbot처럼 상주하지 않는다)
```yaml
  # 상주하지 않고 backup.sh가 `run --rm backup <oci 명령>`으로만 실행한다
  backup:
    image: ghcr.io/oracle/oci-cli:20261028
    profiles: [backup]
    environment:
      OCI_CLI_AUTH: instance_principal
    logging: *default-logging
```
- 이미지는 Oracle 공식 이미지(arm64 포함, 진입점 `oci`, 사용자 `oracle` uid 1000)를 날짜 태그로 고정한다. 메이저 태그 원칙(12-7)의 예외다: 태그는 주간 날짜 태그·`latest`·`main`·`sha-*`뿐이라 메이저 태그가 없고, `latest`는 CLI 메이저가 바뀌어도 따라가 명령·출력이 예고 없이 바뀔 수 있다. 버전을 올릴 때는 12-6 '수동 운영'대로 한다. `restart`·`depends_on`·`ports`는 두지 않는다.

**`infra/backup/backup.sh`** (`set -Eeuo pipefail`, 저장소 루트에서 실행)
1. 이름 `blog-<UTC 시각>.dump`(`date -u +%Y%m%dT%H%M%SZ`, 겹치지 않아 덮어쓸 일이 없다), 임시 파일은 `mktemp`(모드 600), `trap ... EXIT`로 항상 지운다.
2. `docker compose -f infra/docker-compose.prod.yml exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > 임시 파일`. 파이프로 바로 올리지 않는 이유: `pg_dump`가 중간에 실패해도 업로드가 끝까지 가 잘린 백업이 정상처럼 남는다. 파일로 받으면 덤프가 성공한 뒤에만 올린다.
3. `docker compose ... run --rm -T --user "$(id -u):$(id -g)" -v "<임시 파일>:/tmp/<이름>:ro" backup os object put --bucket-name blog-backup --name <이름> --file /tmp/<이름> --no-overwrite --no-multipart`. `--user`: 임시 파일은 실행한 호스트 사용자 소유 600인데, 서버의 `ubuntu`는 uid 1001이라 이미지 사용자 `oracle`(uid 1000)로는 읽지 못한다. 호스트 uid로 실행하면 600을 유지한 채 읽고, 인스턴스 주체 인증은 홈 디렉터리의 설정 파일이 필요 없어 그대로 동작한다. 644로 열면 덤프가 같은 호스트의 다른 사용자에게 보이고, 표준 입력 업로드(`--file -`)는 `--no-multipart`와 함께 쓸 수 없어(CLI가 거부) 택하지 않았다. `--no-multipart`: 128MiB를 넘으면 기본이 멀티파트인데, 실패했을 때 남는 미완료 업로드를 지울 권한(삭제)을 주지 않으므로 한 번에 올린다 (덤프는 그보다 훨씬 작다).
4. 성공: `<UTC 시각> 백업 완료: <이름> (<바이트> bytes)`를 출력, 종료 코드 0.
5. 실패(2·3의 어느 명령이든): `trap ... ERR`로 `<UTC 시각> 백업 실패`를 출력하고 0이 아닌 코드로 끝난다. 재시도하지 않고 다음 날 실행을 기다린다 (관리자 1명의 블로그라 하루치 변경이 적다). 배포(12-6)가 postgres를 다시 만드는 중이면 `exec`가 실패하는데 같은 처리다.

**예약** (`ubuntu` crontab, README에 같은 줄을 적는다. 서버 시간대는 UTC)
```
30 18 * * * cd $HOME/blog && bash infra/backup/backup.sh >> $HOME/backup.log 2>&1
```
- 18:30 UTC = 03:30 KST, 방문이 적은 시각이다. 성공하면 하루 한 줄이라 로그를 따로 정리하지 않는다.
- `bash`로 실행해 실행 권한 비트에 기대지 않는다. 스크립트는 deploy가 체크아웃한 커밋의 것을 쓴다 (12-6).

**OCI 설정** (콘솔. 절차는 README. 12-2 '서버 준비'의 첫 배포 뒤에 한다). 1·3·4는 테넌시에 남아 최초 1회만 하고, 2(규칙의 OCID)와 5(호스트 crontab)는 인스턴스에 묶여 새 서버마다 다시 한다 (아래 '복구' 0·5단계).
1. 버킷 `blog-backup`: 인스턴스와 같은 리전·루트 컴파트먼트, Standard 계층, Private(기본값), 버전 관리 끔.
2. 동적 그룹 `blog-backup`(Default 도메인): 규칙 `instance.id = '<인스턴스 OCID>'`. 인스턴스를 새로 만들면(새 서버) 규칙의 OCID를 새 인스턴스 것으로 바꾼다. 규칙이 그 OCID 하나뿐이라 이전 인스턴스는 그 즉시 권한을 잃는다 (백업도 실패한다). 두 서버를 함께 쓰는 기간은 없어 규칙에 둘을 같이 두지 않는다.
3. 정책(루트 컴파트먼트) 두 줄. 두 번째는 수명 주기 규칙이 객체를 지우는 데 필요하다 (없으면 규칙 저장이 거부된다). `<리전>`은 리전 식별자(예: `ap-chuncheon-1`).
   ```
   Allow dynamic-group blog-backup to manage objects in tenancy where all {target.bucket.name='blog-backup', any {request.permission='OBJECT_CREATE', request.permission='OBJECT_INSPECT', request.permission='OBJECT_READ'}}
   Allow service objectstorage-<리전> to manage object-family in tenancy
   ```
4. 버킷 수명 주기 정책 규칙: 대상 객체, 동작 삭제, 14일, 접두사 없음, 활성. 생성 뒤 14일이 지난 객체를 서비스가 비동기로 지워, 보관 개수는 14~15개다.
5. 위 crontab 줄을 등록한다.

**실패 알아채기**
- 실패는 `~/backup.log`의 `백업 실패` 줄로 남고 메일 등 알림은 없다. M5의 Uptime Kuma에 Push 모니터(정해진 시간 안에 신호가 없으면 알림)를 만들고 crontab 줄 끝에 `&& curl -fsS <Push URL>`을 붙인다 (13장 M5). URL은 비밀값이 아니지만 저장소에 넣지 않고 crontab에만 둔다.
- M5 전까지는 인증서 점검(12-3)과 같은 매주 월요일에 `docker compose -f infra/docker-compose.prod.yml run --rm -T backup os object list --bucket-name blog-backup --fields name,size,timeCreated`로 확인한다. 가장 최근 `time-created`가 24시간 안이고 지난 7일에 하루 하나씩 있으면 정상이다. 아니면 `~/backup.log`를 보고 고친 뒤 스크립트를 직접 한 번 실행한다. README의 백업 절차에 이 점검 명령과 기준을 함께 적는다.

**복구** (README에 적는다. 모두 저장소 폴더 `~/blog`에서)
- 받기: `os object list`(위)로 이름을 고르고 `(umask 077; docker compose -f infra/docker-compose.prod.yml run --rm -T backup os object get --bucket-name blog-backup --name <이름> --file - > ~/restore.dump)`.
- 운영 DB 복구(데이터 손실·새 서버):
  0. 새 서버만: 먼저 'OCI 설정' 2대로 동적 그룹 규칙의 OCID를 새 인스턴스로 바꾸고, `run --rm -T backup os ns get`이 네임스페이스를 출력할 때까지 기다린다 (IAM 변경 반영에 몇 분 걸릴 수 있다). 바꾸기 전에는 '받기'가 `NotAuthorizedOrNotFound`로 실패한다. 10분이 지나도 실패하면 규칙의 OCID와 정책을 콘솔에서 다시 확인한다. 그 뒤 '받기'를 한다.
  1. `docker compose -f infra/docker-compose.prod.yml up -d postgres` (새 서버는 '서버 준비'에서 첫 배포 대신 이것부터 한다), `docker compose ... stop backend`
  2. `docker compose ... exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'` (새 서버에서는 backend가 Flyway로 만든 빈 스키마가 있을 수 있어 항상 다시 만든다)
  3. `docker compose ... exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < ~/restore.dump`. 실패하면 DB가 일부만 복구된 상태이므로 원인(오류 줄)을 고친 뒤 2부터 다시 한다. 받은 파일이 깨졌으면 그 전날 백업을 받는다.
  4. `docker compose ... up -d --no-build` → backend가 `healthy`가 된다 (Flyway 이력도 복구되어 마이그레이션이 이어진다). `rm ~/restore.dump`
  5. 새 서버만: 'OCI 설정' 5대로 crontab 줄을 등록하고(M5 뒤에는 '실패 알아채기'의 Push URL을 붙인 줄) `crontab -l`로 확인한다. crontab은 이전 호스트에만 있어, 빠뜨리면 백업이 알림 없이 멈춘다. 등록 다음 날 '실패 알아채기'의 점검 명령으로 그날 객체가 생겼는지 본다.
  - `admin` 행이 복구되므로 관리자 값 검사(5장 '관리자 계정 생성')는 건너뛰고, 로그인 비밀번호는 백업 시점의 것이다. `.env`의 값과 다르면 12-2 '관리자 비밀번호 변경'을 한다.
- 복구 테스트(새 DB, 운영 DB는 건드리지 않는다):
  1. 직접 백업: `bash infra/backup/backup.sh`가 종료 코드 0이고 출력한 이름을 받는다 (cron 백업 뒤 바뀐 글이 있어도 비교가 어긋나지 않게 새로 만든다).
  2. 위 '받기'로 그 객체를 받는다.
  3. 임시 DB: `docker run -d --rm --name restore-test -e POSTGRES_USER=blog -e POSTGRES_DB=blog -e POSTGRES_HOST_AUTH_METHOD=trust postgres:18` (포트를 열지 않고 compose 네트워크에도 붙지 않아 비밀번호를 두지 않는다), `until docker exec restore-test pg_isready -h localhost -U blog -d blog; do sleep 1; done`. `-h localhost`인 이유: 이미지의 초기화 단계 임시 서버는 소켓으로만 받아, 소켓으로 확인하면 초기화가 끝나기 전에 복구를 시작한다.
  4. `docker exec -i restore-test pg_restore -U blog -d blog --no-owner --exit-on-error < ~/restore.dump`가 종료 코드 0.
  5. 같은 질의 `SELECT (SELECT count(*) FROM post), (SELECT count(*) FROM category)`를 `docker exec restore-test psql -U blog -d blog -Atc '<질의>'`와 운영 `docker compose ... exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "<질의>"'`로 실행해 두 결과가 같다.
  6. `docker stop restore-test`(`--rm`이라 컨테이너와 익명 볼륨이 지워진다), `rm ~/restore.dump`.

**확인 방법** (서버, 이슈 완료 조건)
- 머지 전에는 기능 브랜치로 확인한다: OCI 설정 뒤 서버에서 `git fetch -q origin && git checkout -q --detach origin/chore/#111-db-backup`(구현 세션이 push하면 다시 실행). 추가된 것은 profile 서비스와 스크립트뿐이라 실행 중인 서비스는 바뀌지 않는다. 그사이 main 배포가 있으면 서버가 main 커밋으로 돌아가 cron이 스크립트를 찾지 못하므로 다시 체크아웃한다. 머지 뒤 main 배포가 main 커밋으로 맞춘다.
- 인증: `docker compose -f infra/docker-compose.prod.yml run --rm -T backup os ns get`이 네임스페이스를 출력한다. 컨테이너에서 인스턴스 메타데이터(`169.254.169.254`)에 닿지 못해 실패하면 구현을 멈추고 이슈에 설계 질문으로 남긴다.
- 백업: `bash infra/backup/backup.sh`가 종료 코드 0과 `백업 완료` 줄을 출력하고, `os object list`(위)에 그 이름이 있다. 서버 `/tmp`에 덤프 임시 파일이 남지 않는다.
- 권한: 그 객체에 `run --rm -T backup os object delete --bucket-name blog-backup --name <이름> --force`가 실패하고(`NotAuthorizedOrNotFound`) 객체가 남아 있다.
- 예약: crontab 등록 다음 날 `os object list`에 `time-created`가 그날 18:30 UTC 무렵인 객체가 있고 `~/backup.log`에 `백업 완료` 줄이 있다.
- 보관: OCI Cloud Shell에서 `oci os object-lifecycle-policy get --bucket-name blog-backup`의 규칙이 `action: DELETE`, `time-amount: 14`, `time-unit: DAYS`, `is-enabled: true`다 (서버 권한에는 정책 조회가 없다). 실제 삭제는 14일 뒤라 이슈 범위에서는 설정으로 확인한다.
- 실패 처리(로컬): `infra/.env`에 `POSTGRES_PASSWORD`를 두고 운영 compose의 postgres만 띄운 뒤(`up -d postgres`) 스크립트를 실행하면, 인스턴스 주체를 얻지 못해 업로드가 실패해 `백업 실패` 줄과 0이 아닌 종료 코드로 끝나고 임시 파일이 남지 않는다.
- 복구 테스트: 위 '복구 테스트'대로 해 두 수가 같다. 이슈 코멘트에 객체 이름, 글·카테고리 수, 날짜를 남긴다 (네임스페이스·OCID는 적지 않는다).

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
**deploy 워크플로** (`.github/workflows/deploy.yml`)
- 실행 조건: `push: branches: [main]` + `paths: [backend/**, frontend/**, infra/**, .github/workflows/deploy.yml]`(문서만 바뀐 머지는 배포하지 않는다), 그리고 `workflow_dispatch`(Secrets만 바꿨을 때 main을 다시 배포). 테스트는 PR CI가 머지 조건이라 다시 돌리지 않는다.
- 동시 실행: `concurrency: { group: deploy, cancel-in-progress: false }`. 배포 도중 취소되면 서버가 반쯤 바뀐 상태로 남으므로, 앞 배포가 끝날 때까지 기다린다. 대기 실행은 그룹에 하나만 남고(새 실행이 대기 중이던 실행을 취소한다) 가장 최근 커밋이 배포되므로 그대로 둔다.
- 러너는 `ubuntu-24.04-arm`(GitHub 호스팅 arm64, Public 저장소 무료)에서 네이티브로 빌드한다. QEMU 에뮬레이션은 Gradle·Next 빌드가 수 배 느려 택하지 않았다.
- 빌드·push: `docker/setup-buildx-action` → `docker/login-action`(`ghcr.io`, `GITHUB_TOKEN`, `permissions: packages: write`) → `docker/build-push-action`으로 backend·frontend 각각 `ghcr.io/mojunsul/blog-backend`(`blog-frontend`)에 `:${{ github.sha }}`·`:latest` 두 태그를 push한다. 서버는 `latest`를 쓰지 않고 SHA 태그만 쓴다 (롤백 뒤에도 `latest`는 실패한 빌드를 가리킬 수 있어서).
- GHCR 패키지는 첫 push 뒤 **Public**으로 바꾼다 (패키지 설정, 1회). 이미지에 비밀값이 없고(12-7) 저장소도 Public이라, 서버에 pull 토큰(PAT)을 두지 않기 위해서다.
- 서버 접속은 액션 없이 `ssh`로 한다. Secrets:
  | 이름 | 값 |
  |---|---|
  | `DEPLOY_HOST` | 예약 공인 IP |
  | `DEPLOY_SSH_KEY` | 배포 전용 ed25519 개인 키 (공개 키는 서버 `ubuntu`의 `authorized_keys`, 비밀번호 없음) |
  | `DEPLOY_KNOWN_HOSTS` | `ssh-keyscan -t ed25519 <IP>` 결과 (호스트 키 고정, `StrictHostKeyChecking`을 끄지 않는다) |
  | `POSTGRES_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` | 서버 `.env` 값 (12-2) |
- GitHub 러너의 IP는 고정되지 않으므로 22 포트는 `0.0.0.0/0`에 연다 (12-2 '서버 준비'). 비밀번호 로그인은 OCI Ubuntu 이미지 기본값대로 꺼져 있어 키로만 들어온다.

**`.env` 동기화**: 원본은 GitHub Secrets 하나다. 배포마다 워크플로가 Secrets로 `.env` 내용을 만들어 서버로 보내고, 서버의 `.env`를 손으로 고치지 않는다 (고쳐도 다음 배포에서 덮어써진다). `POSTGRES_DB`·`POSTGRES_USER`는 compose 기본값(`blog`)을 쓰므로 보내지 않는다.
- 값은 run 스텝의 `env:`로 받아 `printf 'POSTGRES_PASSWORD=%s\n...' "$POSTGRES_PASSWORD" ...`로 만들고 ssh 표준 입력으로 보낸다 (`umask 077; cat > ~/blog/infra/.env.next`). 명령 인자·`echo`·`set -x`로 다루지 않아 로그에 남지 않는다 (Actions의 시크릿 마스킹은 보조 수단). Actions는 스텝 로그 머리에 `env:` 목록과 액션의 `with:` 입력(예: `docker/login-action`의 `password`)을 `이름: ***`으로 출력하는데, 값이 출력된 것이 아니라 마스킹된 입력 목록이므로 허용한다 (아래 '확인 방법'의 로그 기준).

**서버 스크립트** `infra/deploy.sh <SHA>`: 워크플로가 `.env.next`를 보낸 뒤 `ssh ... 'cd ~/blog && git fetch -q origin && git cat-file -e <SHA>:infra/deploy.sh && bash <(git show <SHA>:infra/deploy.sh) <SHA>'`로 실행한다 (배포할 커밋의 스크립트를 쓴다. `git show`가 실패하면 `bash`가 빈 스크립트를 받아 0으로 끝나므로 `cat-file -e`로 먼저 확인한다). `set -euo pipefail`.
1. 롤백 지점 보관: `PREV=$(git rev-parse HEAD)`, `cp infra/.env infra/.env.prev`
2. `git checkout -q --detach <SHA>` (compose·Nginx 설정도 같은 커밋으로 맞춘다), `infra/.env` = `.env.next` 내용 + `IMAGE_TAG=<SHA>`, `chmod 600`, `.env.next` 삭제
3. `docker compose -f infra/docker-compose.prod.yml pull backend frontend` → `up -d --no-build`. `git diff --quiet $PREV <SHA> -- infra/nginx`가 실패하면(Nginx 설정이 바뀌었으면) `up -d --no-deps --force-recreate nginx` (마운트 파일 교체는 `up -d`·reload로 반영되지 않는다, 12-2 '서버 준비').
4. 헬스체크(서버 안에서): backend 컨테이너의 health 상태가 `healthy`가 될 때까지 최대 180초(`start_period` 60초 + `retries`×`interval`에 여유) 기다린다. 이어서 Nginx를 거쳐 `curl -fsSk https://localhost/`와 `https://localhost/api/v1/posts`가 둘 다 2xx인지 5초 간격 최대 6회 본다 (Nginx resolver 캐시 10초 동안의 `502`를 넘기기 위해, 12-4). `/`는 frontend와 frontend→backend 연결을, `/api/v1/posts`는 backend와 DB를 확인한다.
5. 성공: `.env.prev` 삭제, backend·frontend의 이전 이미지 정리(디스크가 작다. 이전 이미지는 GHCR에 남는다), 종료 코드 0
   - 정리 대상은 `ghcr.io/mojunsul/blog-*` 중 이번 SHA가 아닌 태그(첫 자동 배포 뒤의 `local` 포함)와 태그 없는 이미지만이다: `docker image ls --filter 'reference=ghcr.io/mojunsul/blog-*' --filter dangling=false --format '{{.Repository}}:{{.Tag}}' | { grep -v ":<SHA>$" || true; } | xargs -r docker rmi`, 이어서 `docker image prune -f`(태그 없는 이미지만. 같은 SHA를 Re-run으로 다시 빌드하면 이전 이미지가 태그를 잃는다). 지울 태그가 없으면(같은 SHA 재배포, 예: Secrets 변경 `workflow_dispatch`) 아무것도 지우지 않는 정상 경우다. `grep -v`는 남는 줄이 없으면 종료 코드 1이라 `pipefail`에서 정리 실패로 가지 않게 `|| true`로 감싼다.
   - `docker image prune -af`를 쓰지 않는 이유: 실행 중이 아닌 이미지를 모두 지워, 갱신 cron이 `run`할 때만 쓰는 certbot 이미지(12-3)까지 지운다. 그러면 갱신 때마다 Docker Hub에서 다시 받아야 하고, 받기에 실패하면 갱신도 실패한다. nginx·postgres·certbot 이미지는 정리 필터가 `blog-*`만 대상으로 하므로 자동 정리되지 않는다. compose에서 버전을 올리면 nginx·postgres는 3단계 `up -d`가 서버에 없는 새 이미지를 pull하고 이전 이미지가 남는다. certbot은 `profiles`라 `up -d`가 받지 않으므로 서버에는 이전 이미지만 남고, 새 이미지는 다음 `run` 때 받는다. 이전 이미지 삭제는 아래 '수동 운영'.
   - 정리는 배포가 끝난 뒤의 일이라, 정리 명령이 실패해도 롤백하지 않고 오류만 출력한 뒤 종료 코드 0으로 끝낸다 (6단계 `trap`에 걸리지 않게 한다).
6. 실패(2~4의 어느 명령이든 실패하면 `trap ... ERR`로 여기로 온다. 이미지 pull 실패도 포함): `docker compose ... logs --tail 50 backend frontend`를 출력하고 `git checkout -q --detach $PREV`, `mv infra/.env.prev infra/.env`, `up -d --no-build`(3과 같은 Nginx 처리), 4를 다시 해 결과를 출력한 뒤 종료 코드 1 → 워크플로 실패(GitHub 실패 알림 메일). 첫 자동 배포의 `PREV`는 서버 빌드 이미지(`IMAGE_TAG=local`)라 그대로 되돌아간다.
- 롤백한 배포의 마이그레이션은 되돌리지 않는다. 그래서 아래 마이그레이션 호환 규칙이 필요하다.
- 빌드·push가 실패하면 서버 접속 스텝까지 가지 않아 서버는 바뀌지 않는다. `.env.next`만 보내고 스크립트 실행 전에 접속이 끊기면 남은 `.env.next`는 다음 배포가 덮어쓴다.
- GHCR의 SHA 태그는 정리하지 않는다 (Public 패키지 용량 무료).

**수동 운영** (README에 적는다)
- 이전 버전으로 되돌리기: Actions에서 되돌릴 커밋의 성공한 deploy 실행을 "Re-run all jobs"한다 (그 커밋으로 다시 빌드·배포). Re-run은 원래 실행 뒤 30일 안에만 되므로, 그보다 오래된 커밋은 `git revert` PR로 되돌린다. 이후 main 머지가 다시 배포하므로, 원인 커밋은 `git revert` PR로 고친다.
- Secrets 변경 반영(관리자 비밀번호 등, 12-2): Secrets를 바꾸고 Actions에서 deploy를 `workflow_dispatch`로 실행한다. `.env` 값이 바뀐 서비스만 compose가 다시 만든다.
- nginx·postgres·certbot·backup 이미지 버전을 올린 뒤: 그 커밋의 배포가 성공하면 서버에서 `docker image ls <저장소>`(저장소 이름은 `nginx`·`postgres`·`certbot/certbot`·`ghcr.io/oracle/oci-cli`)로 이전 태그를 확인하고 `docker image rm <저장소>:<이전 태그>`로 지운다.
  - nginx·postgres: 실행 중인 컨테이너가 쓰는 이미지는 `rm`이 거부하므로 현재 버전은 지워지지 않는다.
  - certbot·backup: 아래는 certbot 기준이고 backup도 같다 (`pull backup`).
  - certbot: 상주하지 않아 위 보호가 없고 배포가 새 이미지를 받지 않으므로, 먼저 `docker compose -f infra/docker-compose.prod.yml pull certbot`(서비스를 지정하면 profile을 켜지 않아도 받는다)으로 새 버전을 받고, 성공한 뒤에만 이전 태그를 지운다. 받기 전에 지우면 다음 갱신 cron의 `run`이 Docker Hub에서 받아야 하고, 받기에 실패하면 갱신도 실패한다. pull이 실패하면 이전 태그를 그대로 두고 나중에 다시 한다.
- 배포 상태 확인: `docker compose -f infra/docker-compose.prod.yml ps --format '{{.Service}} {{.Image}}'`의 backend·frontend 태그가 배포한 SHA다.

**확인 방법** (이슈 완료 조건)
- 머지 전에는 기능 브랜치에서 확인한다: 구현 세션이 `on.push.branches`에 `chore/#110-deploy-workflow`를 임시로 더해 push하고, 확인이 끝나면 PR 전 마지막 커밋에서 뺀다. 서버 저장소는 그동안 브랜치 SHA로 detached 상태가 되고, 머지 뒤 첫 main 배포가 main 커밋으로 되돌린다.
  - 첫 실행은 패키지가 아직 Private이라 서버 pull이 실패해 롤백된다. 두 패키지를 Public으로 바꾼 뒤 그 실행을 다시 실행한다.
  - 자동 반영: push 뒤 사람 개입 없이 워크플로가 성공하고, 위 '배포 상태 확인'의 태그가 그 커밋 SHA, `https://blog.도메인`이 정상이다.
  - 롤백: Secret `JWT_SECRET`을 빈 값이 아닌 짧은 Base64 값 `c2hvcnQ=`(디코딩 6바이트, 10-6대로 기동 실패. Secret은 빈 값을 저장할 수 없다)으로 바꾸고, 경로 필터에 드는 파일을 바꾼 커밋(예: `deploy.yml`에 주석 한 줄)을 push → 워크플로가 실패하고, 로그에 롤백이 찍히고, 태그가 이전 SHA이며 사이트가 정상이다. 확인 뒤 Secret을 원래 값으로 되돌리고, 그 주석은 임시 트리거와 함께 PR 전 마지막 커밋에서 뺀다. 빈 커밋(`--allow-empty`)은 바뀐 파일이 없어 경로 필터에 걸려 실행되지 않고, 같은 SHA의 Re-run은 롤백 전후 SHA가 같아 태그로 롤백을 확인할 수 없어 쓰지 않는다.
  - 로그: 두 실행의 로그 전체(`gh run view --log`)에서 각 Secret 값(`DEPLOY_SSH_KEY`는 본문 한 줄)으로 `grep`해 나오지 않고, `grep -F '***'` 결과에서 스텝 머리 `env:`·`with:` 입력 목록의 `이름: ***` 줄은 허용한다. `GITHUB_TOKEN`을 받는 액션이 스스로 마스킹해 출력한 그 토큰 자리(예: `actions/checkout` 스텝의 `http.https://github.com/.extraheader AUTHORIZATION: basic ***`)도 허용한다. 이 토큰은 위 표의 Secret이 아니고 잡이 끝나면 만료되며, 워크플로의 명령이 출력한 것이 아니기 때문이다. 허용은 그 액션 자신의 스텝 안 줄로 한정한다 (워크플로의 run 스텝에서 같은 모양이 나오면 아래 기준으로 판정한다). 그 밖의 `***` 줄은 Actions가 Secret 값과 같은 문자열을 로그 어디서나 바꾸므로 값이 출력된 것인지 우연히 같은 문자열인지 가른다.
    - 줄 앞의 스텝 이름과 앞뒤 줄로 어느 명령의 출력인지(예: 롤백의 `compose logs`) 찾고, `***` 자리에 Secret 값을 대입해 원문을 복원한다.
    - 복원한 원문이 코드·라이브러리의 고정 문구 일부(예: `ADMIN_USERNAME`이 `admin`일 때 `/api/v1/admin` 경로)이면 우연한 일치로 통과다. 값이 들어가는 자리(`이름=값`, 명령 인자, 설정·환경 출력, 인증 헤더)이거나 원문을 복원할 수 없으면 값이 출력된 것으로 보고 실패다 (출력 경로를 고치고 다시 확인한다).
    - 통과로 본 줄은 이슈 코멘트에 스텝 이름과 출처 명령만 적는다 (Public 이슈라 Secret 값과 복원한 원문은 적지 않는다).
  - 경로 필터: 필터 밖 파일만 바꾼 커밋(예: README 커밋)을 그 커밋 하나만 담아 push한다 (push의 경로 필터는 push 전후 차이 전체로 판정하므로 필터 안 파일을 바꾼 커밋과 함께 push하면 실행된다). `gh run list --workflow deploy.yml --commit <SHA>`가 비어 있다.
- 머지 후: main 머지 커밋의 deploy가 성공하고 태그가 머지 커밋 SHA다.

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
