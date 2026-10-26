# Blog

개인 개발 블로그. Spring Boot(`backend/`) + Next.js(`frontend/`), 서버는 OCI 한 대에서 Docker Compose로 운영한다. 설계는 `docs/architecture.md`.

아래 명령의 `blog.도메인`·`<이메일>`은 자리표시자다. 실제 값은 서버에서 명령을 실행할 때만 입력하고 저장소에는 남기지 않는다.

## 서버 구성

`infra/docker-compose.prod.yml`: nginx, certbot, frontend, backend, postgres. 호스트에 여는 포트는 nginx의 80·443뿐이다. 비밀값은 서버의 `infra/.env`에만 둔다.

## 최초 배포 (서버, 1회)

순서: 서버 준비 → DNS → `.env` → 인증서 발급 → 첫 배포 → 갱신 cron. 인증서가 없으면 nginx가 뜨지 않으므로 발급이 배포보다 먼저다.

1. **서버 준비** (OCI 콘솔)
   - `VM.Standard.A1.Flex`(arm64) 4 OCPU·24GB, Ubuntu 24.04, 예약 공인 IP를 붙인다.
   - 보안 목록 수신 TCP: 80·443은 `0.0.0.0/0`, 22는 본인 IP(`/32`)만. 본인 IP가 바뀌면 22 규칙을 고친다.
   - 인스턴스 iptables: `/etc/iptables/rules.v4`에서 22 규칙 다음 줄에 아래를 넣고 `sudo iptables-restore < /etc/iptables/rules.v4`로 적용한다. Docker 설치 전에 한다 (적용하면 Docker가 만든 규칙이 지워진다).
     ```
     -A INPUT -p tcp -m state --state NEW -m tcp --dport 80 -j ACCEPT
     -A INPUT -p tcp -m state --state NEW -m tcp --dport 443 -j ACCEPT
     ```
   - Docker: [공식 문서](https://docs.docker.com/engine/install/ubuntu/)의 apt 저장소 방식으로 `docker-ce`·`docker-compose-plugin`을 설치하고 `sudo usermod -aG docker ubuntu` 후 다시 로그인한다.
2. **저장소**: 이 이슈의 설정은 머지 전 확인이 필요해 기능 브랜치로 시작한다.
   ```bash
   git clone https://github.com/mojunsul/blog.git ~/blog && cd ~/blog
   git checkout chore/#109-server-https
   ```
3. **DNS**: Cloudflare DNS에 A 레코드 `blog` → 예약 공인 IP, 프록시 상태는 **DNS only**(회색 구름). `dig +short blog.도메인`이 그 IP를 돌려주는지 확인한다.
4. **`.env`**: `cp infra/.env.example infra/.env && chmod 600 infra/.env` 후 채운다 (`JWT_SECRET`은 `openssl rand -base64 32`). 서버 첫 배포는 `IMAGE_TAG=local`.
5. **인증서 최초 발급** (저장소 폴더에서)
   ```bash
   docker run -d --rm --name acme -p 80:80 -v "$PWD/infra/certbot/www:/usr/share/nginx/html:ro" nginx:1-alpine
   ```
   ```bash
   docker compose -f infra/docker-compose.prod.yml run --rm certbot certonly --webroot -w /var/www/certbot --cert-name blog -d blog.도메인 -m <이메일> --agree-tos --no-eff-email --dry-run
   ```
   성공하면 `--dry-run`을 빼고 다시 실행한다. 실패하면 오류(DNS·80 포트)를 고친 뒤 `--dry-run`부터 다시 한다. 끝나면 임시 Nginx를 내린다.
   ```bash
   docker stop acme
   ```
6. **첫 배포**: 서버에서 빌드한다.
   ```bash
   docker compose -f infra/docker-compose.prod.yml up -d --build
   ```
7. **갱신 cron**: `crontab -e`에 한 줄을 추가한다.
   ```
   17 3 * * * cd $HOME/blog && { docker compose -f infra/docker-compose.prod.yml run --rm -T certbot renew -q && docker compose -f infra/docker-compose.prod.yml exec -T nginx nginx -s reload; } >> $HOME/certbot-renew.log 2>&1
   ```

PR이 머지되면 돌아가기 전에 브랜치 끝 SHA를 적어 둔다.

```bash
git rev-parse HEAD
```

```bash
git checkout main && git pull
```

```bash
git diff <브랜치 끝 SHA> HEAD -- infra
```

차이가 없으면 끝이다. 차이가 있으면 모든 서비스를 다시 만든다 (마운트한 Nginx 설정을 반영하려면 컨테이너를 다시 만들어야 한다).

```bash
docker compose -f infra/docker-compose.prod.yml up -d --force-recreate
```

## 인증서 갱신

`certbot renew`는 수명의 약 1/3이 남은 인증서만 갱신하므로 매일 도는 cron이 대부분 아무것도 하지 않는다. 실패하면 reload를 건너뛰고 `~/certbot-renew.log`에 오류가 남는다. Let's Encrypt는 만료 안내 메일을 보내지 않으므로 매주 월요일 남은 기간을 확인한다.

```bash
docker compose -f infra/docker-compose.prod.yml run --rm certbot certificates
```

`VALID: N days`가 수명의 1/3 미만이면 `~/certbot-renew.log`를 보고 고친다. 수명은 아래 두 날짜의 차이다 (90일 인증서는 30일, 64일 인증서는 21일 미만이 기준. Let's Encrypt는 2027-02-10부터 기본 64일로 발급한다).

```bash
sudo openssl x509 -noout -startdate -enddate -in infra/certbot/conf/live/blog/cert.pem
```

수동 확인:

```bash
docker compose -f infra/docker-compose.prod.yml run --rm certbot renew --dry-run
```

## 관리자 비밀번호 변경

저장소 폴더(`~/blog`)에서:

1. `infra/.env`의 `ADMIN_PASSWORD`를 바꾼다.
2. 기존 계정을 지운다 (`refresh_token`도 함께 지워져 로그인 상태가 풀린다. 이미 발급된 Access 토큰은 만료(15분)까지 남는다).
   ```bash
   docker compose -f infra/docker-compose.prod.yml exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DELETE FROM admin"'
   ```
3. backend를 다시 만든다. `restart`는 바뀐 `.env` 값을 읽지 않는다.
   ```bash
   docker compose -f infra/docker-compose.prod.yml up -d --no-deps backend
   ```
4. 새 비밀번호로 로그인한다. backend가 기동하지 않으면 `docker compose -f infra/docker-compose.prod.yml logs backend`의 값 검사 오류를 보고 `.env`를 고쳐 3을 다시 한다.

## 로컬에서 운영 compose 띄우기

443 블록 때문에 인증서가 있어야 nginx가 뜬다. 자체 서명 인증서를 같은 경로에 만든다.

```bash
mkdir -p infra/certbot/conf/live/blog && openssl req -x509 -nodes -newkey rsa:2048 -days 30 -subj /CN=localhost -keyout infra/certbot/conf/live/blog/privkey.pem -out infra/certbot/conf/live/blog/fullchain.pem
```

```bash
IMAGE_TAG=local docker compose -f infra/docker-compose.prod.yml up -d --build
```

`https://localhost`(브라우저는 경고 화면에서 계속 진행, `curl -k`)로 접속한다. `http://localhost`는 `301`이다.
