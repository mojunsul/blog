# Blog

개인 개발 블로그. Spring Boot(`backend/`) + Next.js(`frontend/`), 서버는 OCI 한 대에서 Docker Compose로 운영한다. 설계는 `docs/architecture.md`.

아래 명령의 `blog.도메인`·`<이메일>`은 자리표시자다. 실제 값은 서버에서 명령을 실행할 때만 입력하고 저장소에는 남기지 않는다.

## 서버 구성

`infra/docker-compose.prod.yml`: nginx, certbot, frontend, backend, postgres. 호스트에 여는 포트는 nginx의 80·443뿐이다. 비밀값의 원본은 GitHub Secrets이고, 서버의 `infra/.env`는 자동 배포부터 deploy가 매번 다시 쓴다.

## 배포

main에 머지하면(`backend/`·`frontend/`·`infra/`·`deploy.yml`이 바뀐 경우, 문서만 바뀌면 배포하지 않는다) `.github/workflows/deploy.yml`이 arm64 이미지를 빌드해 GHCR에 push하고(태그: 커밋 SHA + `latest`), SSH로 서버에서 `infra/deploy.sh <SHA>`를 실행한다. 헬스체크가 실패하면 이전 커밋·`.env`로 롤백하고 워크플로를 실패시킨다(GitHub 실패 알림 메일).

- **Secrets** (저장소 Settings → Secrets and variables → Actions)

  | 이름 | 값 |
  |---|---|
  | `DEPLOY_HOST` | 예약 공인 IP |
  | `DEPLOY_SSH_KEY` | 배포 전용 ed25519 개인 키 (공개 키는 서버 `ubuntu`의 `authorized_keys`) |
  | `DEPLOY_KNOWN_HOSTS` | `ssh-keyscan -t ed25519 <IP>` 결과 |
  | `POSTGRES_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD` | 서버 `.env` 값 |

  배포 전용 키는 `ssh-keygen -t ed25519 -N '' -f deploy_key`로 만들고, 개인 키는 Secret에만 두고 로컬 파일은 지운다.
- **GHCR 패키지**: 첫 push 뒤 `blog-backend`·`blog-frontend` 패키지를 Public으로 바꾼다 (패키지 설정, 1회). 서버에 pull 토큰을 두지 않기 위해서다.
- **이전 버전으로 되돌리기**: Actions에서 되돌릴 커밋의 성공한 deploy 실행을 "Re-run all jobs"한다. 30일이 지난 실행은 Re-run할 수 없으므로 `git revert` PR로 되돌린다. 이후 main 머지가 다시 배포하므로 원인 커밋은 `git revert` PR로 고친다.
- **Secrets 변경 반영**: Secrets를 바꾸고 Actions에서 deploy를 `workflow_dispatch`로 실행한다.
- **배포 결과 확인**: Actions 탭의 deploy 실행이 성공이면 반영된 것이고, 실패하면 로그에 롤백이 찍힌다.
- **배포 상태 확인** (서버 `~/blog`): backend·frontend 태그가 배포한 SHA다.
  ```bash
  docker compose -f infra/docker-compose.prod.yml ps --format '{{.Service}} {{.Image}}'
  ```

## 최초 배포 (서버, 1회)

순서: 서버 준비 → DNS → `.env` → 인증서 발급 → 첫 배포 → 갱신 cron. 인증서가 없으면 nginx가 뜨지 않으므로 발급이 배포보다 먼저다.

1. **서버 준비** (OCI 콘솔)
   - `VM.Standard.A1.Flex`(arm64) 4 OCPU·24GB, Ubuntu 24.04, 예약 공인 IP를 붙인다.
   - 보안 목록 수신 TCP: 80·443·22 모두 `0.0.0.0/0`. 22는 deploy의 GitHub 러너 IP가 고정되지 않아 열고, 키 인증만 허용한다 (`/etc/ssh/sshd_config`의 `PasswordAuthentication no` 확인).
   - 인스턴스 iptables: `/etc/iptables/rules.v4`에서 22 규칙 다음 줄에 아래를 넣고 `sudo iptables-restore < /etc/iptables/rules.v4`로 적용한다. Docker 설치 전에 한다 (적용하면 Docker가 만든 규칙이 지워진다).
     ```
     -A INPUT -p tcp -m state --state NEW -m tcp --dport 80 -j ACCEPT
     -A INPUT -p tcp -m state --state NEW -m tcp --dport 443 -j ACCEPT
     ```
   - Docker: [공식 문서](https://docs.docker.com/engine/install/ubuntu/)의 apt 저장소 방식으로 `docker-ce`·`docker-compose-plugin`을 설치하고 `sudo usermod -aG docker ubuntu` 후 다시 로그인한다.
2. **저장소**
   ```bash
   git clone https://github.com/mojunsul/blog.git ~/blog && cd ~/blog
   ```
   #109 머지 전 검증 때만 이 단계 뒤에 `git checkout chore/#109-server-https`를 하고, 마지막의 "#109 머지 전 검증 뒤 main으로 복귀"를 따른다. 머지 후 새로 구성할 때는 둘 다 건너뛴다.
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
7. **자동 배포 준비**: 위 '배포'의 Secrets와 GHCR 패키지 Public 전환. `.env` 값은 같은 값을 Secrets에도 등록한다. 첫 자동 배포부터 deploy가 `.env`를 다시 쓰고 `IMAGE_TAG`를 커밋 SHA로 바꾼다.
8. **갱신 cron**: `crontab -e`에 한 줄을 추가한다.
   ```
   17 3 * * * cd $HOME/blog && { docker compose -f infra/docker-compose.prod.yml run --rm -T certbot renew -q && docker compose -f infra/docker-compose.prod.yml exec -T nginx nginx -s reload; } >> $HOME/certbot-renew.log 2>&1
   ```

### #109 머지 전 검증 뒤 main으로 복귀

기능 브랜치에서 검증했을 때만 한다. PR이 머지되면 돌아가기 전에 브랜치 끝 SHA를 적어 둔다.

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

1. GitHub Secret `ADMIN_PASSWORD`를 바꾼다 (서버 `.env`는 deploy가 덮어쓰므로 직접 고치지 않는다).
2. 기존 계정을 지운다 (`refresh_token`도 함께 지워져 로그인 상태가 풀린다. 이미 발급된 Access 토큰은 만료(15분)까지 남는다).
   ```bash
   docker compose -f infra/docker-compose.prod.yml exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DELETE FROM admin"'
   ```
3. Actions에서 deploy를 `workflow_dispatch`로 실행한다. `.env`가 바뀌어 backend 컨테이너가 다시 만들어진다 (`restart`는 바뀐 값을 읽지 않는다).
4. 새 비밀번호로 로그인한다. backend가 기동하지 않으면 `docker compose -f infra/docker-compose.prod.yml logs backend`의 값 검사 오류를 보고 Secret을 고쳐 3을 다시 한다. 기동하지 못하면 deploy가 이전 `.env`로 롤백하므로 이전 비밀번호로 계정이 다시 만들어진다.

## 로컬에서 운영 compose 띄우기

443 블록 때문에 인증서가 있어야 nginx가 뜬다. 자체 서명 인증서를 같은 경로에 만든다.

```bash
mkdir -p infra/certbot/conf/live/blog && openssl req -x509 -nodes -newkey rsa:2048 -days 30 -subj /CN=localhost -keyout infra/certbot/conf/live/blog/privkey.pem -out infra/certbot/conf/live/blog/fullchain.pem
```

```bash
IMAGE_TAG=local docker compose -f infra/docker-compose.prod.yml up -d --build
```

`https://localhost`(브라우저는 경고 화면에서 계속 진행, `curl -k`)로 접속한다. `http://localhost`는 `301`이다.
