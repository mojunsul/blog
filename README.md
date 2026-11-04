# Blog

개인 개발 블로그. Spring Boot(`backend/`) + Next.js(`frontend/`), 서버는 OCI 한 대에서 Docker Compose로 운영한다. 설계는 `docs/architecture.md`.

아래 명령의 `blog.도메인`·`<이메일>`은 자리표시자다. 실제 값은 서버에서 명령을 실행할 때만 입력하고 저장소에는 남기지 않는다.

## 서버 구성

`infra/docker-compose.prod.yml`: nginx, certbot, frontend, backend, postgres, backup. certbot·backup은 `profiles`라 상주하지 않고 `run`으로만 실행한다. 호스트에 여는 포트는 nginx의 80·443뿐이다. 비밀값의 원본은 GitHub Secrets이고, 서버의 `infra/.env`는 자동 배포부터 deploy가 매번 다시 쓴다.

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
- **nginx·postgres·certbot·backup 이미지 버전을 올린 뒤**: 배포가 성공하면 서버에서 `docker image ls <저장소>`(`nginx`·`postgres`·`certbot/certbot`·`ghcr.io/oracle/oci-cli`)로 이전 태그를 확인하고 `docker image rm <저장소>:<이전 태그>`로 지운다. nginx·postgres는 실행 중인 이미지를 `rm`이 거부하므로 현재 버전은 지워지지 않는다. certbot·backup은 먼저 새 버전을 받고(`pull certbot` / `pull backup`), 성공한 뒤에만 이전 태그를 지운다 (받기 전에 지우면 다음 갱신·백업 때 받기에 실패할 수 있다).
  ```bash
  docker compose -f infra/docker-compose.prod.yml pull certbot
  ```
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

## DB 백업과 복구

호스트 crontab이 매일 `infra/backup/backup.sh`를 실행한다: postgres 컨테이너의 `pg_dump -Fc` → 임시 파일 → `backup` 서비스(OCI CLI)가 Object Storage 버킷 `blog-backup`에 업로드. 인증은 인스턴스 주체라 `.env`·GitHub Secrets에 값이 없다. 보관은 버킷 수명 주기 규칙(14일)이 하고, 서버 권한에는 삭제가 없다.

### OCI 설정 (첫 배포 뒤)

1·3·4는 테넌시에 남아 최초 1회만 하고, 2·5는 인스턴스에 묶여 새 서버마다 다시 한다. 이하 콘솔 작업이다.

1. **버킷** `blog-backup`: 인스턴스와 같은 리전·루트 컴파트먼트, Standard, Private, 버전 관리 끔.
2. **동적 그룹** `blog-backup`(Default 도메인): 규칙 `instance.id = '<인스턴스 OCID>'`. 새 서버면 OCID를 새 인스턴스 것으로 바꾼다 (이전 인스턴스는 즉시 권한을 잃는다).
3. **정책**(루트 컴파트먼트) 두 줄. 두 번째는 수명 주기 규칙이 객체를 지우는 데 필요하다. `<리전>`은 리전 식별자(예: `ap-chuncheon-1`).
   ```
   Allow dynamic-group blog-backup to manage objects in tenancy where all {target.bucket.name='blog-backup', any {request.permission='OBJECT_CREATE', request.permission='OBJECT_INSPECT', request.permission='OBJECT_READ'}}
   Allow service objectstorage-<리전> to manage object-family in tenancy
   ```
4. **버킷 수명 주기 정책 규칙**: 대상 객체, 동작 삭제, 14일, 접두사 없음, 활성.
5. **crontab**: `crontab -e`에 한 줄을 추가한다 (서버 시간대 UTC, 18:30 = 03:30 KST).
   ```
   30 18 * * * cd $HOME/blog && bash infra/backup/backup.sh >> $HOME/backup.log 2>&1
   ```

인증 확인 (저장소 폴더에서). 네임스페이스가 출력되면 된다. IAM 변경 반영에 몇 분 걸릴 수 있다.

```bash
docker compose -f infra/docker-compose.prod.yml run --rm -T backup os ns get
```

### 수동 백업과 주간 점검

```bash
bash infra/backup/backup.sh
```

종료 코드 0과 `백업 완료` 줄이 나오면 성공이다. 실패하면 `백업 실패` 줄과 0이 아닌 코드로 끝나고, 재시도 없이 다음 날 실행을 기다린다. 실패는 `~/backup.log`에만 남고 알림은 없다 (M5에 Uptime Kuma Push 모니터를 붙이고 crontab 줄 끝에 `&& curl -fsS <Push URL>`을 더한다. URL은 저장소에 넣지 않는다).

M5 전까지는 인증서 점검과 같은 매주 월요일에 객체 목록을 본다.

```bash
docker compose -f infra/docker-compose.prod.yml run --rm -T backup os object list --bucket-name blog-backup --fields name,size,timeCreated
```

가장 최근 `time-created`가 24시간 안이고 지난 7일에 하루 하나씩 있으면 정상이다. 아니면 `~/backup.log`를 보고 고친 뒤 스크립트를 직접 한 번 실행한다.

### 복구

모두 저장소 폴더 `~/blog`에서 한다. `<이름>`은 위 목록에서 고른다.

**받기**

```bash
(umask 077; docker compose -f infra/docker-compose.prod.yml run --rm -T backup os object get --bucket-name blog-backup --name <이름> --file - > ~/restore.dump)
```

**운영 DB 복구** (데이터 손실·새 서버)

0. 새 서버만: 위 'OCI 설정' 2로 동적 그룹 규칙의 OCID를 새 인스턴스로 바꾸고, 인증 확인이 네임스페이스를 출력할 때까지 기다린 뒤 '받기'를 한다. 바꾸기 전에는 `NotAuthorizedOrNotFound`로 실패한다. 새 서버는 '최초 배포'의 1부터 5까지를 마친 뒤 6(첫 배포) 대신 이 절차를 하고, `infra/.env`의 `IMAGE_TAG`는 비워 둔다 (4단계가 `latest`를 GHCR에서 받는다. `local`이면 로컬 이미지가 없어 실패한다).
1. postgres만 띄우고 backend를 멈춘다.
   ```bash
   docker compose -f infra/docker-compose.prod.yml up -d postgres && docker compose -f infra/docker-compose.prod.yml stop backend
   ```
2. DB를 다시 만든다 (새 서버에는 backend가 Flyway로 만든 빈 스키마가 있을 수 있다).
   ```bash
   docker compose -f infra/docker-compose.prod.yml exec -T postgres sh -c 'dropdb -U "$POSTGRES_USER" "$POSTGRES_DB" && createdb -U "$POSTGRES_USER" "$POSTGRES_DB"'
   ```
3. 복구한다. 실패하면 DB가 일부만 복구된 상태이므로 오류 줄을 고친 뒤 2부터 다시 한다. 받은 파일이 깨졌으면 그 전날 백업을 받는다.
   ```bash
   docker compose -f infra/docker-compose.prod.yml exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' < ~/restore.dump
   ```
4. 서비스를 띄우고 backend가 `healthy`가 되면 덤프를 지운다.
   ```bash
   docker compose -f infra/docker-compose.prod.yml up -d --no-build && rm ~/restore.dump
   ```
5. 새 서버만: 'OCI 설정' 5의 crontab 줄을 등록하고 `crontab -l`로 확인한다. 빠뜨리면 백업이 알림 없이 멈춘다. 등록 다음 날 주간 점검 명령으로 그날 객체가 생겼는지 본다.

`admin` 행도 복구되므로 로그인 비밀번호는 백업 시점의 것이다. `.env`의 값과 다르면 '관리자 비밀번호 변경'을 한다.

**복구 테스트** (임시 DB, 운영 DB는 건드리지 않는다)

1. `bash infra/backup/backup.sh`로 새 백업을 만들고(종료 코드 0) 출력된 이름을 '받기'로 받는다.
2. 임시 DB를 띄우고 준비될 때까지 기다린다 (`-h localhost`: 초기화 단계의 임시 서버는 소켓으로만 받아, 소켓으로 확인하면 초기화 전에 복구를 시작한다).
   ```bash
   docker run -d --rm --name restore-test -e POSTGRES_USER=blog -e POSTGRES_DB=blog -e POSTGRES_HOST_AUTH_METHOD=trust postgres:18
   ```
   ```bash
   until docker exec restore-test pg_isready -h localhost -U blog -d blog; do sleep 1; done
   ```
3. 복구하고 종료 코드가 0인지 본다.
   ```bash
   docker exec -i restore-test pg_restore -U blog -d blog --no-owner --exit-on-error < ~/restore.dump
   ```
4. 같은 질의를 임시 DB와 운영 DB에서 실행해 두 결과가 같은지 본다.
   ```bash
   docker exec restore-test psql -U blog -d blog -Atc 'SELECT (SELECT count(*) FROM post), (SELECT count(*) FROM category)'
   ```
   ```bash
   docker compose -f infra/docker-compose.prod.yml exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT (SELECT count(*) FROM post), (SELECT count(*) FROM category)"'
   ```
5. 정리한다 (`--rm`이라 컨테이너와 볼륨이 지워진다).
   ```bash
   docker stop restore-test && rm ~/restore.dump
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
