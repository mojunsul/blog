# 블로그 프로젝트 설계 문서

> Claude Code에서 작업을 시작하기 위한 기준 문서.
> 저장소 루트에 `docs/architecture.md`로 두고, `CLAUDE.md`에서 이 문서를 참조하도록 한다.
> 이 문서와 구현이 충돌하면 이 문서를 먼저 수정한 뒤 구현한다.

---

## 0. 프로젝트 개요

- **목적**: 개인 개발 블로그 + 백엔드/인프라 학습용 포트폴리오
- **작성자**: 관리자 1명 (회원가입 기능 없음)
- **글 작성 방식**: Markdown (DB에 TEXT로 저장)
- **댓글**: Giscus (자체 댓글 테이블 없음)
- **배포**: Oracle Cloud Always Free 인스턴스 + 개인 도메인
  - 블로그: `blog.도메인`, 토이 프로젝트: 각자 별도 서브도메인

---

## 1. 기술 스택

### 프론트엔드
| 기술 | 용도 |
|---|---|
| Next.js 16 (App Router) + TypeScript | 서버 렌더링 + 캐시 기반 블로그 화면, SEO |
| Tailwind CSS | 스타일링 |
| react-markdown + remark-gfm + rehype-slug + Shiki(`@shikijs/rehype`) | Markdown 렌더링, 코드 하이라이팅 |
| `@tailwindcss/typography` | Markdown 본문 스타일 (`prose`) |
| react-hook-form + Zod (`@hookform/resolvers`) | 관리자 폼 처리·검증 |
| openapi-typescript | 백엔드 OpenAPI 명세에서 TS 타입 자동 생성 |
| Vitest | `lib/`의 순수 함수 단위 테스트 |

### 백엔드
| 기술 | 용도 |
|---|---|
| Java 21 + Spring Boot (Initializr 최신 안정 버전) + Gradle | API 서버 |
| Spring Web, Validation | REST API, 입력 검증 |
| Spring Data JPA + QueryDSL | 기본 CRUD + 동적 검색 쿼리 |
| Spring Security + JWT (HttpOnly 쿠키) + CSRF 보호 | 관리자 인증·인가 |
| Flyway | DB 스키마 마이그레이션 |
| springdoc-openapi | Swagger UI, OpenAPI 명세 (로컬 전용) |
| Spring Actuator | 헬스체크 (`health`만 노출) |
| JUnit5 + Testcontainers | 실제 PostgreSQL로 통합 테스트 |

### 데이터베이스
- PostgreSQL 18 (로컬·테스트·운영 동일 버전, 검색은 초기에 내장 기능 사용, Redis는 고도화 단계에서 도입)

### 인프라
| 기술 | 용도 |
|---|---|
| Docker + Docker Compose | 로컬/서버 실행 환경 통일 |
| Nginx + Certbot | 리버스 프록시, HTTPS, rate limiting (학습 목적으로 Caddy 대신 선택) |
| GitHub Actions + GHCR | CI/CD, 이미지 레지스트리 |
| Oracle Cloud (ARM 인스턴스) | 서버 (이미지는 `linux/arm64`로 빌드) |
| OCI Object Storage | DB 백업 저장, (M6) 이미지 업로드 |
| Cloudflare DNS | 도메인·서브도메인 관리 |

### 부가 기능
- Giscus(댓글), Umami(방문자 분석), Uptime Kuma(모니터링), sitemap/RSS

---

## 2. 저장소 만들기

### 2-1. 저장소 방식: 모노레포
프론트·백엔드·인프라를 **하나의 저장소**에서 관리한다.
- 저장소 이름 예시: `blog`
- 공개 여부: **Public** (포트폴리오). 비밀번호, 비밀번호 해시, 시크릿, `.env`는 절대 커밋하지 않는다.

### 2-2. 생성 절차
1. GitHub에서 저장소 생성 (README 체크, `.gitignore`는 직접 작성)
2. 로컬로 clone 후 디렉터리 생성
   ```bash
   mkdir backend frontend infra docs .github
   ```
3. **백엔드 생성** (Spring Initializr → `backend/`에 압축 해제)
   - Gradle, Java 21
   - Group: `io.github.mojunsul`, Artifact: `blog`, Package: `io.github.mojunsul.blog`
   - Dependencies: Spring Web, Validation, Spring Data JPA, PostgreSQL Driver, Spring Security, Flyway Migration, Spring Boot Actuator, Lombok, Testcontainers, (선택) DevTools
   - `build.gradle`에 직접 추가
     - `springdoc-openapi-starter-webmvc-ui` (Spring Boot 버전과 호환되는 springdoc 버전 확인)
     - JWT: `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson`
     - QueryDSL: `io.github.openfeign.querydsl` 포크 (annotation processor 설정 포함)
4. **프론트엔드 생성** (`frontend/`에서)
   ```bash
   npx create-next-app@latest .
   # TypeScript: Yes / ESLint: Yes / Tailwind CSS: Yes
   # src/ directory: Yes / App Router: Yes / import alias: @/*
   ```
   - 설치된 Next.js 버전을 확인하고, 이 문서의 Next.js 관련 내용(특히 `proxy.ts`)이 버전과 맞는지 점검한다.
5. 루트 `.gitignore` 작성 (2-3)
6. 첫 커밋: `chore: initialize monorepo with backend and frontend`
7. GitHub 설정: `main` 브랜치 보호 (PR 필수, CI 통과 필수)

### 2-3. 루트 .gitignore 핵심 항목
```gitignore
# env / secrets
.env
.env.*
!.env.example

# backend
backend/build/
backend/.gradle/
backend/out/

# frontend
frontend/node_modules/
frontend/.next/
frontend/out/

# IDE / OS
.idea/
.vscode/
*.iml
.DS_Store
```
> `frontend/src/types/api.d.ts`(자동 생성 타입)는 **커밋한다.** CI에서 백엔드 없이 프론트 빌드가 가능해야 하기 때문.

### 2-4. 브랜치·커밋 규칙 (GitHub Flow)
- `main`: 항상 배포 가능한 상태, 직접 push 금지
- 작업 브랜치: `feat/#이슈번호-설명`, `fix/#...`, `refactor/#...`, `chore/#...`
  - 예: `feat/#3-post-list-api`
- 흐름: 이슈 → 브랜치 → 구현 → PR → 리뷰(Copilot) → `main` 머지 → 자동 배포
- 커밋 메시지: Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`, `ci:`)
- 이슈는 마일스톤(M0~M6) 단위로 묶고, **이슈 하나 = PR 하나**를 기본으로 한다.

---

## 3. 전체 아키텍처

```
사용자 브라우저
     │ https://blog.도메인
     ▼
┌──────────── OCI 인스턴스 (Docker Compose) ────────────┐
│  Nginx (:80, :443) + Certbot                          │
│   ├ /api/*  → backend  (Spring Boot :8080)            │
│   └ 그 외   → frontend (Next.js :3000)                 │
│                                                       │
│  backend ──→ postgres (:5432, 외부 비공개)              │
│  frontend ─(서버 컴포넌트 fetch)→ http://backend:8080   │
│  backup (cron) ─ pg_dump → OCI Object Storage         │
└───────────────────────────────────────────────────────┘
```

**핵심 결정**
- API는 `api.` 서브도메인이 아니라 **같은 도메인의 `/api` 경로**로 연결한다.
  - same-origin이므로 CORS 설정이 필요 없다.
  - 인증 쿠키가 별도 도메인 설정 없이 전달된다.
- Nginx `proxy_pass http://backend:8080;` (끝에 `/` 없음) → `/api` 경로가 그대로 Spring에 전달된다.
- `/actuator`, `/swagger-ui`, `/v3/api-docs`는 `/api`로 시작하지 않으므로 외부에서 Spring에 도달하지 않는다. 헬스체크는 Docker 네트워크 내부(`http://backend:8080/actuator/health`)에서만 호출한다.
- PostgreSQL 포트는 외부에 열지 않는다.
- Spring은 프록시 뒤에서 동작하므로 `server.forward-headers-strategy: framework`를 설정한다.
- **모든 시간은 UTC로 저장**하고, API는 ISO-8601 + 오프셋 형식으로 응답한다. 표시용 시간대 변환(KST)은 프론트에서 한다.

---

## 4. 저장소 전체 구조

```
blog/
├ backend/                 # Spring Boot
├ frontend/                # Next.js
├ infra/
│  ├ docker-compose.yml          # 로컬용 (postgres만)
│  ├ docker-compose.prod.yml     # 서버용 (nginx, certbot, frontend, backend, postgres, backup)
│  ├ .env.example                # 필요한 환경변수 목록 (값은 비움)
│  ├ nginx/
│  │  ├ nginx.conf               # 공통 설정 (gzip, 로그 형식, rate limit zone)
│  │  └ conf.d/
│  │     └ blog.conf             # blog.도메인 server 블록
│  └ backup/
│     └ backup.sh                # pg_dump → 압축 → Object Storage 업로드
├ docs/
│  ├ architecture.md             # 이 문서
│  └ verification.md             # 검증 세션 규칙 (CLAUDE.md에서 분리)
├ .github/
│  └ workflows/
│     ├ backend-ci.yml     # backend/** 변경 시: 테스트, 빌드
│     ├ frontend-ci.yml    # frontend/** 변경 시: lint, typecheck, build
│     └ deploy.yml         # main 머지 시: arm64 이미지 빌드 → GHCR → SSH 배포
├ CLAUDE.md
├ .gitignore
└ README.md
```

---

## 5. 백엔드 구조

**도메인(기능) 단위 패키지**로 구성한다.

```
backend/src/main/java/io/github/mojunsul/blog/
├ BlogApplication.java
├ global/
│  ├ config/          # QuerydslConfig, JpaAuditingConfig, OpenApiConfig, ClockConfig, PasswordEncoderConfig, AdminProperties, AuthProperties
│  ├ security/        # SecurityConfig, JwtProvider, JwtAuthenticationFilter, CookieUtil,
│  │                  # ProblemDetailAuthenticationEntryPoint, ProblemDetailAccessDeniedHandler, ProblemDetailRequestRejectedHandler
│  ├ exception/       # ErrorCode, BusinessException, GlobalExceptionHandler, ProblemDetailFactory, ProblemDetailResponseWriter
│  ├ init/            # AdminInitializer, LocalDataInitializer (ApplicationRunner)
│  ├ logging/         # RequestIdFilter
│  └ common/          # BaseTimeEntity, PageResponse
└ domain/
   ├ post/
   │  ├ controller/   # PostController(공개), AdminPostController(관리자)
   │  ├ service/      # PostService
   │  ├ repository/   # PostRepository, PostQueryRepository(QueryDSL)
   │  ├ entity/       # Post, PostStatus
   │  └ dto/          # PostSearchRequest, PostCreateRequest, PostUpdateRequest, PostSummaryResponse, PostDetailResponse ...
   ├ category/
   ├ tag/             # Tag, PostTag
   └ auth/            # AuthController, AuthService, Admin·AdminRepository, RefreshToken

backend/src/main/resources/
├ application.yaml          # 공통
├ application-local.yaml    # 로컬 DB, Swagger 활성화
├ application-prod.yaml     # 운영 (값은 환경변수 주입, Swagger 비활성화)
└ db/migration/
   └ V1__init_schema.sql

backend/src/test/java/io/github/mojunsul/blog/
├ support/
│  ├ TestContainers.java               # PostgreSQL 컨테이너 하나를 모든 테스트가 공유
│  ├ RepositoryTestSupport.java        # @DataJpaTest + Testcontainers + @ActiveProfiles("test")
│  └ IntegrationTestSupport.java       # @SpringBootTest + Testcontainers + @ActiveProfiles("test")
├ fixture/                              # 엔티티별 테스트 데이터 (PostFixture ...)
└ domain/...                            # 도메인별 Repository / Service / Controller 테스트
```

**백엔드 규칙**
- 스키마 변경은 **반드시 Flyway 마이그레이션 파일**로 한다. `spring.jpa.hibernate.ddl-auto: validate`.
- 이미 적용된 마이그레이션 파일은 수정하지 않고 새 버전 파일을 추가한다.
- 마이그레이션에 비밀번호, 비밀번호 해시 등 비밀값을 넣지 않는다.
- 엔티티를 API 응답으로 직접 반환하지 않는다. 요청·응답 DTO는 Java `record`로 작성한다.
- 예외는 `BusinessException(ErrorCode)`로 던지고 `GlobalExceptionHandler`가 ProblemDetail 형식으로 변환한다 (9장).
- `@ManyToMany`를 쓰지 않고 연결 엔티티(`PostTag`)로 매핑한다.
- 시간 타입은 엔티티에서 `Instant`를 사용하고, 테스트 가능하도록 `Clock` 빈을 주입받는다.
- 검증은 프론트(Zod)와 무관하게 **서버에서 반드시 다시 한다** (`@Valid` + Bean Validation).
- Actuator는 `health`만 노출한다. Swagger는 `local` 프로필에서만 활성화한다.
- Spring Data `Page`를 그대로 직렬화하지 않고 `PageResponse`로 변환해서 반환한다.
- 목록·검색 API의 쿼리 파라미터는 검증 어노테이션을 붙인 요청 record(`@Valid @ModelAttribute`, 예: `PostSearchRequest`)로 받는다. 컨트롤러에서 `Pageable`을 직접 받지 않는다 (임의 `sort` 노출 방지, M4 검색 조건 확장).
- 목록 정렬은 Repository 쿼리에 고정해 Repository 테스트로 검증한다.
- **로컬 시드 데이터**: `LocalDataInitializer`(`@Profile("local")`, `ApplicationRunner`)가 엔티티와 Repository로 저장한다. 글이 하나라도 있으면 아무것도 하지 않는다. Flyway 마이그레이션에는 스키마만 둔다 (시드를 SQL로 두면 엔티티·스키마 변경을 따라가지 못하고, 버전 번호가 실제 마이그레이션과 충돌한다).
- **테스트 프로필**: DB를 쓰는 테스트는 Support 클래스의 `@ActiveProfiles("test")`로 `test` 프로필에서 실행한다 (`spring.profiles.default: local` 때문에 프로필을 지정하지 않으면 local 설정과 시드가 테스트에 섞인다).
  - 테스트 프로필은 `test` 하나로 둔다. 특정 테스트만 설정이 달라야 하면 프로필을 바꾸지 않고 `@SpringBootTest(properties = ...)`로 그 값만 바꾼다.
  - `build.gradle`에서 프로필을 지정하지 않는다 (`@ActiveProfiles`와 섞이면 시스템 프로퍼티가 덮어써진다).
  - M2에서 프로필에 따라 동작이 달라지는 Controller 테스트가 생기면 `ControllerTestSupport`를 두고 같은 어노테이션을 붙인다.

**로깅**
- 예외 로그는 `GlobalExceptionHandler` 한 곳에서만 남긴다. 요청 종류(GET·POST)와 무관하다.
  - 5xx(처리되지 않은 예외): `ERROR` + 스택 트레이스
  - 4xx(`BusinessException`): `WARN` 한 줄 (`code`, 요청 경로), 스택 트레이스 없음
  - 프레임워크가 만드는 4xx(입력 검증 실패, JSON 파싱 실패, 405, Security 필터의 401·403, 방화벽 거절 400 등)는 남기지 않는다. 클라이언트 입력 문제라 서버에서 조치할 것이 없고, 공개 API라 봇 요청으로 로그가 쉽게 늘어난다.
  - 서비스에서 예외를 잡아 로그를 남긴 뒤 다시 던지지 않는다 (같은 에러가 두 번 기록된다).
- 정상 흐름에서는 조회 로그를 남기지 않는다 (요청 기록은 Nginx 접근 로그가 담당). 서비스에서는 관리자 행위(로그인 성공·실패, 글·카테고리 생성·수정·삭제)와 보안 이벤트(Refresh 토큰 재사용 감지)만 `INFO`/`WARN`으로 남긴다 (M2).
  - 로그인 성공은 `AuthService`가 `INFO`로 username만 남긴다. 로그인 실패는 `BusinessException(INVALID_CREDENTIALS)`라 `GlobalExceptionHandler`의 `WARN`(`code`, 요청 경로)이 그 기록이고, 서비스에서 따로 남기지 않는다 (같은 실패가 두 번 기록되지 않게). 실패 로그에 입력된 username을 넣지 않는다: 비밀번호를 username 칸에 잘못 입력하면 그대로 로그에 남고, 줄바꿈이 든 값으로 로그 줄을 위조할 수 있다. 누가 시도했는지는 `requestId`로 Nginx 접근 로그(IP)와 연결해 본다.
- **요청 추적 ID**: `RequestIdFilter`가 모든 요청에 ID를 정해 MDC(`requestId`)에 넣고, 응답 헤더 `X-Request-Id`로 돌려준다. 로그 패턴에 `requestId`를 출력해 한 요청의 로그를 묶어 본다.
  - 요청에 `X-Request-Id`가 있고 형식(`^[A-Za-z0-9-]{1,64}$`)에 맞으면 그 값을 쓰고, 없거나 형식이 다르면 UUID를 만든다 (로그 위조 방지).
  - 필터는 Security 필터보다 먼저 실행해 인증 실패 로그에도 ID가 찍히게 하고, 요청이 끝나면 MDC를 비운다.
  - M3부터는 Nginx가 `$request_id`를 `X-Request-Id`로 넘겨 Nginx 접근 로그와 앱 로그를 같은 ID로 연결한다 (12-4).
- 출력은 표준 출력만 쓴다 (운영은 Docker `json-file` 드라이버가 수집, 12-2 용량 제한). 파일 appender, JSON 구조화 로그는 쓰지 않는다.
- SQL 로그(`org.hibernate.SQL: debug`)는 `local` 프로필에서만 켠다.
- 비밀번호, 토큰, 쿠키, `Authorization` 헤더, 요청 본문 전체는 로그에 남기지 않는다.

**관리자 계정 생성**
- 회원가입 API는 만들지 않는다.
- `AdminInitializer`(`ApplicationRunner`)가 앱 시작 시 관리자 계정이 없으면 환경변수 `ADMIN_USERNAME`, `ADMIN_PASSWORD`로 생성한다 (BCrypt).
- 이미 존재하면 아무것도 하지 않는다. 계정이 하나라도 있으면(`count() > 0`) 환경변수의 username이 달라도 새로 만들지 않는다.
- 설정: `application.yaml`의 `blog.admin.username: ${ADMIN_USERNAME:}`, `blog.admin.password: ${ADMIN_PASSWORD:}`, `blog.admin.required: false`를 `@ConfigurationProperties` record `global/config/AdminProperties`로 받는다 (`BlogApplication`의 `@ConfigurationPropertiesScan`으로 등록). `application-prod.yaml`은 `required: true`.
- 관리자가 없을 때 값에 따른 결과 ("비어 있음"은 빈 문자열이거나 공백만 있는 값, `isBlank()`)
  | username | password | `required: false` (local, test) | `required: true` (prod) |
  |---|---|---|---|
  | 비어 있음 | 비어 있음 | `WARN` 한 줄 후 건너뜀 | `IllegalStateException` |
  | 비어 있음 | 값 있음 | `IllegalStateException` (`blog.admin.username` 누락) | 같음 |
  | 값 있음 | 비어 있음 | `IllegalStateException` (`blog.admin.password` 누락) | 같음 |
  | 값 있음 | 값 있음 | 아래 검증을 통과하면 생성, 어기면 `IllegalStateException` | 같음 |
  - prod에서 둘 다 비면 기동을 멈춘다. 관리자 없이 운영 서버가 뜨면 로그인할 방법이 없고, 설정 누락이 조용히 숨는다.
  - local·test에서 둘 다 비면 건너뛴다. 공개 화면 개발에는 관리자가 필요 없고, 저장소가 Public이라 local 기본 비밀번호를 설정 파일에 둘 수 없다. local에서 로그인하려면 IDE 실행 설정이나 셸에 두 환경변수를 넣는다.
  - 한쪽만 있으면 프로필과 무관하게 설정 실수로 보고 멈춘다. 메시지에는 빠진 설정 키 이름만 넣는다.
- 값 검증 (두 값이 모두 있을 때)
  - username: 50자 이하(`admin.username` 컬럼 길이), 앞뒤 공백 없음(`strip()`한 값과 같아야 함). 로그인 때 정확히 같은 값을 입력해야 하므로 공백을 몰래 지우지 않고 설정 오류로 본다.
  - password: 12자 이상, UTF-8 72바이트 이하. BCrypt는 72바이트까지만 쓰고 Spring Security는 넘는 값을 거절하므로, 기동 때 미리 막는다. 공백을 포함한 모든 문자를 허용한다.
- 오류 메시지와 로그에 비밀번호 값을 넣지 않는다 (username은 넣어도 된다).
  - `AdminProperties`는 record라 자동 생성되는 `toString()`에 `password` 원문이 들어간다. `toString()`을 재정의해 `password`는 값이 있으면 `****`, 비어 있으면 빈 값으로 표시한다 (객체를 그대로 로그나 예외 메시지에 넣어도 새지 않게).
- 생성하면 `INFO`로 username만 남긴다 (5장 관리자 행위 로그).
- `PasswordEncoder` 빈은 `BCryptPasswordEncoder`(기본 강도)로 `global/config/PasswordEncoderConfig`에 둔다. `SecurityConfig`와 분리해 Security 설정 변경과 섞이지 않게 한다.
- `Admin` 엔티티는 `BaseTimeEntity`를 상속하지 않는다 (`admin` 테이블에 `updated_at`이 없다). `createdAt`만 `@CreatedDate`로 채운다.
- 비밀번호·username 변경: API를 두지 않는다. 서버에서 `admin` 행을 지우고(`refresh_token`은 `ON DELETE CASCADE`로 함께 삭제) `infra/.env`의 값을 바꾼 뒤 backend를 재시작하면 새 값으로 다시 만들어진다 (12-2).

---

## 6. DB 설계

```sql
admin
  id            BIGINT GENERATED BY DEFAULT AS IDENTITY PK
  username      VARCHAR(50)  UNIQUE NOT NULL
  password      VARCHAR(100) NOT NULL          -- BCrypt
  created_at    TIMESTAMPTZ  NOT NULL

category
  id            BIGINT PK
  name          VARCHAR(50)  NOT NULL
  slug          VARCHAR(100) UNIQUE NOT NULL
  created_at    TIMESTAMPTZ  NOT NULL
  updated_at    TIMESTAMPTZ  NOT NULL

post
  id            BIGINT PK
  title         VARCHAR(200) NOT NULL
  slug          VARCHAR(200) UNIQUE NOT NULL   -- URL용 (/posts/{slug})
  summary       VARCHAR(500)
  content       TEXT         NOT NULL         -- Markdown
  thumbnail_url VARCHAR(500)
  status        VARCHAR(20)  NOT NULL         -- DRAFT / PUBLISHED
  view_count    BIGINT       NOT NULL DEFAULT 0
  category_id   BIGINT       FK → category ON DELETE SET NULL
  published_at  TIMESTAMPTZ                   -- 최초 발행 시각
  created_at    TIMESTAMPTZ  NOT NULL
  updated_at    TIMESTAMPTZ  NOT NULL
  INDEX (status, published_at DESC)

tag
  id            BIGINT PK
  name          VARCHAR(50)  UNIQUE NOT NULL  -- 정규화된 값 저장

post_tag
  post_id       BIGINT FK → post ON DELETE CASCADE
  tag_id        BIGINT FK → tag
  PRIMARY KEY (post_id, tag_id)
  INDEX (tag_id)

refresh_token
  id            BIGINT PK
  admin_id      BIGINT       FK → admin ON DELETE CASCADE
  token_hash    VARCHAR(64)  UNIQUE NOT NULL  -- SHA-256 해시, 원문 저장 금지
  expires_at    TIMESTAMPTZ  NOT NULL
  created_at    TIMESTAMPTZ  NOT NULL
```

**데이터 규칙**
- 공개 API는 `status = PUBLISHED`인 글만 조회한다.
- `published_at`은 글이 **처음** PUBLISHED가 될 때 서버가 설정하고, 이후 수정해도 바뀌지 않는다.
- 삭제는 하드 삭제. 카테고리 삭제 시 글의 `category_id`는 NULL이 된다.
- **slug 규칙**: 작성 폼에서 직접 입력. 영문 소문자·숫자·하이픈만 허용 (`^[a-z0-9]+(?:-[a-z0-9]+)*$`), 최대 200자. 프론트(Zod)와 서버 양쪽에서 검증한다.
- **태그 규칙**: 저장 전 앞뒤 공백 제거 + 소문자 변환. 최대 50자, 글당 최대 10개. 없는 태그는 자동 생성한다.
- 조회수(`view_count`)는 M6에서 구현한다. 글 조회(GET)에서 증가시키지 않고 별도 API로 분리한다 (8-2).
- Refresh 토큰은 초기에는 DB 저장, M6에서 Redis 이전을 검토한다. 만료된 토큰은 주기적으로 정리한다 (`@Scheduled`).

---

## 7. API 공통 규칙

- **Base path**: `/api/v1`
- **형식**: JSON (`Content-Type: application/json`), 에러는 `application/problem+json`
- **응답 래퍼 없음**: 성공 응답은 `{ data: ... }` 같은 공통 래퍼 없이 본문을 그대로 반환하고, 성공·실패는 HTTP 상태 코드로 구분한다 (에러가 표준 ProblemDetail이라 래퍼를 쓰면 형식이 비대칭이 되고, 클라이언트가 같은 출처의 프론트 하나뿐이라 응답 규약을 통일할 이유가 없다).
- **필드 이름**: camelCase
- **시간**: ISO-8601 UTC (`"2026-10-11T01:00:00Z"`)
- **페이지네이션**: `page`(0~9999, 기본 0), `size`(1~50, 기본 10)
  - `page` 상한이 없으면 offset(`page × size`)이 int 범위를 넘어 500이 난다. 9999는 개인 블로그가 닿지 않는 값이고, `size` 50에서도 offset이 int 범위 안에 든다.
  - 범위를 벗어나면 자동 보정하지 않고 `400` (`INVALID_INPUT`).
  - 문자열을 숫자로 바꾸는 것은 Spring 바인딩 기본 동작을 따르고, 서버가 보장하는 것은 **바뀐 값의 범위 검사**다 (Spring Boot 4.1.1, `@WebMvcTest`로 확인).
    - 빈 값·공백만 있는 값(`?page=`, `?page=%20`): 값이 없는 것으로 보고 기본값.
    - 숫자로 읽히는 값은 관대하게 받는다: 앞뒤 공백(`%201`), `+` 부호(`%2B1`), 앞자리 0(`01`), `-0`, 16진수(`0x10`, `%2310` → 16). 바뀐 값에 위 범위 검사를 하므로 `0x2710`(10000)은 `400`.
    - 숫자로 읽히지 않는 값(`abc`, `1.0`, `1e2`)과 Integer 범위를 넘는 값: `400` (`INVALID_INPUT`, `reason`은 "형식이 올바르지 않습니다", 9장).
    - 같은 키가 여러 개면 첫 값만 바꾸고 검사한다: `?page=1&page=abc`는 `page=1`, `?page=abc&page=1`은 `400`, `?size=1&size=2`는 `size=1`.
    - 엄격하게 숫자만 받지 않는 이유: API는 프론트 서버 컴포넌트만 정해진 형식(`page=0`, 숫자만)으로 부르고, 관대하게 받은 값도 범위 검사를 거쳐 잘못된 페이지나 500이 되지 않는다. 거절하려면 문자열로 받아 직접 파싱해야 해 국내 Spring 실무의 기본 바인딩 관례에서 벗어나고 코드가 늘어난다. 프론트 URL의 `?page`는 사용자가 직접 고치는 값이라 숫자만 받는다(11장).
    - 위 동작(빈 값·공백, 앞자리 0, 16진수와 그 범위 검사, 같은 키 여러 개)은 Controller 테스트로 고정해, 프레임워크 업그레이드로 바뀌면 드러나게 한다. 테스트는 인코딩된 쿼리 문자열을 그대로 보내 디코딩까지 포함한다 (MockMvc는 쿼리의 `+`를 공백으로 바꾸지 않아 `+`는 테스트하지 않는다).
  - `sort` 파라미터는 받지 않는다. 정렬은 API마다 고정한다.
- **식별자**: 공개 API는 `slug`, 관리자 API는 `id`
- **수정**: `PUT`은 전체 교체 (작성과 같은 본문을 보냄)

### 상태 코드
| 코드 | 언제 |
|---|---|
| `200 OK` | 조회·수정 성공 |
| `201 Created` | 생성 성공 (`Location` 헤더 포함) |
| `204 No Content` | 삭제, 로그아웃, 재발급 성공, CSRF 토큰 발급 |
| `400 Bad Request` | 입력값 검증 실패 |
| `401 Unauthorized` | 인증 안 됨, 토큰 만료 |
| `403 Forbidden` | 권한 없음, CSRF 토큰 불일치 |
| `404 Not Found` | 리소스 없음 |
| `409 Conflict` | slug 중복 등 |
| `429 Too Many Requests` | rate limit 초과 (Nginx) |

---

## 8. API 명세

### 8-1. 공개 API (인증 불필요)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/v1/posts` | 글 목록 (필터·검색) |
| GET | `/api/v1/posts/{slug}` | 글 상세 |
| GET | `/api/v1/categories` | 카테고리 목록 + 글 개수 |
| GET | `/api/v1/tags` | 태그 목록 + 글 개수 |

#### GET `/api/v1/posts`
쿼리 파라미터 (모두 선택): `page`, `size`, `category`(slug), `tag`(이름), `q`(제목·본문 키워드)

```
GET /api/v1/posts?page=0&size=10&tag=spring
```
`200 OK`
```json
{
  "content": [
    {
      "id": 12,
      "title": "Spring Security + JWT 정리",
      "slug": "spring-security-jwt",
      "summary": "HttpOnly 쿠키로 JWT 인증 구현하기",
      "thumbnailUrl": null,
      "category": { "name": "Backend", "slug": "backend" },
      "tags": ["spring", "jwt"],
      "publishedAt": "2026-10-11T01:00:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 23,
  "totalPages": 3,
  "hasNext": true
}
```
- 목록에는 본문(`content` Markdown)을 포함하지 않는다.
- 정렬: `publishedAt` 내림차순, 같으면 `id` 내림차순 (발행 시각이 같은 글이 페이지 사이에서 중복·누락되지 않도록).
- 카테고리가 없는 글은 `"category": null`.
- `category`, `tag`, `q` 필터와 `tags` 값은 M4에서 구현한다. 그 전까지 `tags`는 항상 `[]`.

#### GET `/api/v1/posts/{slug}`
`200 OK`
```json
{
  "id": 12,
  "title": "Spring Security + JWT 정리",
  "slug": "spring-security-jwt",
  "summary": "HttpOnly 쿠키로 JWT 인증 구현하기",
  "content": "# 들어가며\n본문 Markdown...",
  "thumbnailUrl": null,
  "category": { "name": "Backend", "slug": "backend" },
  "tags": ["spring", "jwt"],
  "publishedAt": "2026-10-11T01:00:00Z",
  "updatedAt": "2026-10-11T03:30:00Z"
}
```
- 없거나 DRAFT인 글: `404` (`POST_NOT_FOUND`)
- 조회수는 이 API에서 증가시키지 않는다 (프론트 캐시 때문에 실제 방문 수와 맞지 않고, GET이 상태를 바꾸면 안 되기 때문).
- 경로의 `slug`는 형식을 검사하지 않고 그대로 조회한다. 규칙에 맞지 않거나 대소문자가 다른 값(`Spring-Jwt`)도 `404`다 (식별자라서 "그런 글이 없다"가 맞는 응답이다).
  - 단, 서버가 경로 형식을 거절하는 값은 조회 전에 `400`이다 (10-5): Spring Security 방화벽이 거절하는 값(`;` 등)은 ProblemDetail `INVALID_INPUT`, Tomcat이 먼저 거절하는 인코딩된 `/`(`%2F`)는 Tomcat 기본 응답.

#### GET `/api/v1/categories`
```json
[
  { "id": 1, "name": "Backend", "slug": "backend", "postCount": 8 },
  { "id": 2, "name": "Infra", "slug": "infra", "postCount": 3 }
]
```
- 글이 없는 카테고리도 `postCount: 0`으로 포함한다. 관리자 카테고리 화면(M2)도 이 API로 목록을 조회한다 (관리자용 목록 API는 따로 두지 않는다).
- `postCount`는 PUBLISHED 글 기준.
- 정렬: `name` 오름차순, 같으면 `id` 오름차순. 페이지네이션 없이 배열로 응답한다.

#### GET `/api/v1/tags`
```json
[
  { "name": "spring", "postCount": 5 },
  { "name": "jwt", "postCount": 2 }
]
```
- `postCount`는 PUBLISHED 글 기준.

### 8-2. 조회수 API (M6)
| Method | URL | 설명 |
|---|---|---|
| POST | `/api/v1/posts/{slug}/views` | 조회수 1 증가, `204` |

- 브라우저(클라이언트 컴포넌트)에서 상세 페이지 진입 시 호출한다.
- 같은 방문자의 반복 증가 방지는 M6에서 설계한다 (예: 쿠키·Redis 기반 중복 제거).
- 인증이 필요 없는 상태 변경 요청이므로 CSRF 검사 대상에서 제외하고 rate limit을 적용한다.
- 증가는 직접 UPDATE 쿼리(`view_count = view_count + 1`, `@Modifying`)로 한다. 엔티티 변경 감지를 쓰면 `@LastModifiedDate`가 `updatedAt`을 조회 시각으로 바꾸고, 동시 요청에서 증가분이 유실된다.

### 8-3. 인증 API

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/v1/auth/csrf` | CSRF 토큰 쿠키 발급 |
| POST | `/api/v1/auth/login` | 로그인 |
| POST | `/api/v1/auth/refresh` | Access 재발급 + Refresh Rotation |
| POST | `/api/v1/auth/logout` | 로그아웃 |
| GET | `/api/v1/auth/me` | 로그인 상태 확인 |

#### GET `/api/v1/auth/csrf`
- 인증 불필요. 본문 없이 `204`.
- 요청에 `XSRF-TOKEN` 쿠키가 없으면 토큰을 새로 만들어 쿠키로 내려보낸다 (속성은 10-1·10-5).
  ```
  Set-Cookie: XSRF-TOKEN=...; Path=/; SameSite=Lax        (HTTPS 요청이면 Secure 추가)
  ```
- 이미 쿠키가 있으면 그 토큰을 그대로 쓰므로 `Set-Cookie`가 없다 (Spring Security 기본 동작).
- `csrf.spa()`는 모든 요청에서 토큰을 미리 불러오므로 다른 API 응답에도 같은 쿠키가 붙는다 (10-5). 그래도 이 API를 두는 이유는 프론트가 로그인·관리자 화면 진입 때 부르는 명시적 진입점이 필요해서다. 첫 상태 변경 요청(로그인 POST) 전에 다른 API를 우연히 먼저 불렀는지에 기대지 않는다.

#### POST `/api/v1/auth/login`
요청 (헤더 `X-XSRF-TOKEN` 필요, 10장 참고)
```json
{ "username": "admin", "password": "********" }
```
`200 OK`
```
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Lax; Path=/api; Max-Age=900
Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Path=/api/v1/auth; Max-Age=1209600
```
```json
{ "username": "admin" }
```
- 실패: `401` (`INVALID_CREDENTIALS`). 아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않는다 (응답 본문이 같고, 응답 시간도 같게 한다. 10-6).
- local 프로필에서는 두 쿠키에 `Secure`가 없다 (10-1).
- 요청 검증 (`LoginRequest` record, `@Valid @RequestBody`). 검증 실패는 `400` `INVALID_INPUT`, `errors`에 필드별 사유.
  | 필드 | 규칙 | 어기면 |
  |---|---|---|
  | username | 필수(`null`·빈 값·공백만 있는 값 거절, `@NotBlank`), 50자 이하(`admin.username` 컬럼) | `400` |
  | password | 필수(`null`·빈 값 거절, `@NotEmpty`). 공백만 있는 값은 허용 | `400` |
  - password에 `@NotBlank`를 쓰지 않는 이유: 관리자 비밀번호는 공백을 포함한 모든 문자를 허용하므로(5장 '관리자 계정 생성') 공백만으로 된 비밀번호도 로그인할 수 있어야 한다. 길이 상한은 검증에 두지 않고 아래처럼 인증 실패로 처리한다.
  - password가 UTF-8 72바이트를 넘으면 `401` `INVALID_CREDENTIALS`. BCrypt는 비교할 때 72바이트 뒤를 잘라 버려 앞 72바이트가 같으면 맞다고 하므로(Spring Security 7.1.1 `BCrypt.checkpw`), 저장할 수 없는 길이(5장)의 입력은 서비스에서 실패로 본다.
  - username은 앞뒤 공백을 지우지 않고 대소문자까지 그대로 비교한다 (`admin `, `Admin`은 `admin`과 다른 값이라 `401`). 관리자 생성 때 앞뒤 공백을 금지하므로(5장) 정확히 같은 값만 맞다.
  - 본문이 없거나 JSON 파싱에 실패하면 `400` `INVALID_INPUT` (`errors` `[]`, 9장 기존 처리). 필드 값이 배열·객체여도 같다. 숫자·불리언은 Jackson 기본 변환으로 문자열이 된다 (`"username": 1`은 `"1"`).
  - `Content-Type`이 JSON이 아니면 Spring 기본 `415` (ProblemDetail, `errors` `[]`, `code` 없음. 9장 기존 처리). 프론트는 항상 JSON으로 보낸다.
  - 같은 키가 여러 개면 마지막 값을 쓴다 (Jackson 기본, `{"username":"a","username":"b"}`는 `b`). 알 수 없는 필드는 무시한다 (Jackson 3 기본).
  - 같은 키, 숫자 값 변환 동작은 Controller 테스트로 고정한다 (7장 페이지네이션처럼 프레임워크 업그레이드로 바뀌면 드러나게).
- 이미 로그인한 상태에서 다시 로그인해도 새 토큰을 발급하고 기존 Refresh 토큰은 그대로 둔다 (다른 기기의 로그인 유지). 만료된 행 정리는 #70.

#### POST `/api/v1/auth/refresh`
- 본문 없음. `refresh_token` 쿠키로 인증.
- 성공: 새 `access_token`, 새 `refresh_token` 발급, 기존 Refresh 토큰 삭제, `204`
- 만료·위조·이미 사용된 토큰: `401` (`INVALID_REFRESH_TOKEN`). 이미 사용된 토큰이 다시 들어오면 탈취로 간주하고 해당 관리자의 Refresh 토큰을 모두 삭제한다.

#### POST `/api/v1/auth/logout`
- DB의 Refresh 토큰 삭제, 두 쿠키 `Max-Age=0`, `204`

#### GET `/api/v1/auth/me`
- 로그인 상태: `200` + `{ "username": "admin" }`, 아니면 `401` (`UNAUTHORIZED`, `AuthenticationEntryPoint`).
- username은 `access_token`의 `sub`에서 꺼내고 DB를 조회하지 않는다. 그래서 관리자 행을 지운 뒤에도 남은 Access 토큰은 만료(최대 15분)까지 `200`이다 (비밀번호 변경 절차 직후 잠깐 생기는 차이라 받아들인다, 5장).
- 응답 DTO는 로그인과 같은 `AdminResponse(username)`.

### 8-4. 관리자 API (`/api/v1/admin/**`, ROLE_ADMIN)

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/v1/admin/posts` | 전체 목록 (DRAFT 포함, `?status=` 필터) |
| GET | `/api/v1/admin/posts/{id}` | 수정용 단건 조회 |
| POST | `/api/v1/admin/posts` | 글 작성 |
| PUT | `/api/v1/admin/posts/{id}` | 글 수정 |
| DELETE | `/api/v1/admin/posts/{id}` | 글 삭제 |
| POST | `/api/v1/admin/categories` | 카테고리 생성 |
| PUT | `/api/v1/admin/categories/{id}` | 카테고리 수정 |
| DELETE | `/api/v1/admin/categories/{id}` | 카테고리 삭제 (글의 카테고리는 NULL) |

- 상태 변경 요청(POST/PUT/DELETE)은 모두 `X-XSRF-TOKEN` 헤더가 필요하다.
- 인증 없음: `401`, 권한 없음·CSRF 불일치: `403`

#### POST `/api/v1/admin/posts`
요청
```json
{
  "title": "Spring Security + JWT 정리",
  "slug": "spring-security-jwt",
  "summary": "HttpOnly 쿠키로 JWT 인증 구현하기",
  "content": "# 들어가며\n...",
  "thumbnailUrl": null,
  "categoryId": 1,
  "tags": ["Spring", " jwt "],
  "status": "PUBLISHED"
}
```
`201 Created`
```
Location: /api/v1/admin/posts/12
```
```json
{ "id": 12, "slug": "spring-security-jwt" }
```
- 태그는 정규화되어 `["spring", "jwt"]`로 저장된다.
- 검증 실패: `400` (`INVALID_INPUT`), slug 중복: `409` (`DUPLICATE_SLUG`), 없는 카테고리: `404` (`CATEGORY_NOT_FOUND`)

**검증 규칙**
| 필드 | 규칙 |
|---|---|
| title | 필수, 1~200자 |
| slug | 필수, 6장 slug 규칙 |
| summary | 선택, 최대 500자 |
| content | 필수 |
| categoryId | 선택 |
| tags | 선택, 최대 10개, 각 1~50자 |
| status | 필수, `DRAFT` 또는 `PUBLISHED` |

#### GET `/api/v1/admin/posts/{id}`
`200 OK` — 상세 응답에 아래 필드 추가
```json
{
  "id": 12,
  "status": "PUBLISHED",
  "categoryId": 1,
  "createdAt": "2026-10-10T12:00:00Z",
  "...": "공개 상세와 동일한 나머지 필드"
}
```

#### PUT `/api/v1/admin/posts/{id}`
- 본문은 작성과 동일 (전체 교체). `200` + 수정된 상세 응답.

#### DELETE `/api/v1/admin/posts/{id}`
- `204`. 없는 글: `404`.

#### 카테고리 생성·수정 요청
```json
{ "name": "Backend", "slug": "backend" }
```

---

## 9. 에러 응답 형식 (ProblemDetail, RFC 9457)

Spring의 `ProblemDetail`을 사용하고, 확장 필드로 `code`, `errors`를 추가한다.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "입력값이 올바르지 않습니다.",
  "instance": "/api/v1/admin/posts",
  "code": "INVALID_INPUT",
  "errors": [
    { "field": "title", "reason": "제목을 입력하세요" }
  ]
}
```
- 프론트는 `code`로 분기하고 `detail`을 사용자 메시지로 쓴다.
- `errors`는 검증 실패 시에만 채운다 (그 외에는 빈 배열).
- `errors[].reason`에는 사용자에게 보여 줄 수 있는 메시지만 넣는다. 타입 변환 실패(예: `?page=abc`)처럼 프레임워크가 만든 메시지(클래스명, 예외 메시지)는 내보내지 않고 `"형식이 올바르지 않습니다"`로 바꾼다. 타입별 메시지(`messages.properties`의 `typeMismatch`)는 쓰지 않는다 (타입이 늘 때 빠뜨리면 다시 내부 메시지가 나가므로).
- 500 에러는 내부 정보(스택 트레이스, SQL)를 노출하지 않는다.

### ErrorCode 목록 (초기)
| code | status | 설명 |
|---|---|---|
| `INVALID_INPUT` | 400 | 입력값 검증 실패 |
| `INVALID_CREDENTIALS` | 401 | 로그인 실패 |
| `UNAUTHORIZED` | 401 | 인증 필요, Access 토큰 만료 |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh 토큰 무효 |
| `FORBIDDEN` | 403 | 권한 없음 |
| `INVALID_CSRF_TOKEN` | 403 | CSRF 토큰 불일치 |
| `POST_NOT_FOUND` | 404 | 글 없음 |
| `CATEGORY_NOT_FOUND` | 404 | 카테고리 없음 |
| `DUPLICATE_SLUG` | 409 | slug 중복 (글·카테고리) |
| `INTERNAL_ERROR` | 500 | 서버 오류 |

> Spring Security 필터 단계에서 발생하는 401/403도 같은 형식으로 응답하도록 `AuthenticationEntryPoint`, `AccessDeniedHandler`를 구현한다 (10-5).

**ProblemDetail 생성 코드 공유** (형식이 `GlobalExceptionHandler`와 Security 필터 두 군데로 갈라지지 않게)
- `global/exception/ProblemDetailFactory`: `ErrorCode`, `instance`, `errors`로 9장 형식의 `ProblemDetail`을 만드는 정적 메서드. `GlobalExceptionHandler`의 `problem()`과 `FieldError` record를 여기로 옮기고, `GlobalExceptionHandler`는 이를 호출한다.
- `global/exception/ProblemDetailResponseWriter`(`@Component`): 필터 단계처럼 컨트롤러 밖에서 `HttpServletResponse`에 직접 쓸 때 쓴다. 상태 코드, `Content-Type: application/problem+json`, UTF-8을 설정하고 `ProblemDetailFactory`로 만든 본문(`instance`는 요청 URI, `errors`는 `[]`)을 쓴다.
  - JSON 직렬화는 Spring Boot가 등록한 `JsonMapper` 빈(Jackson 3, `tools.jackson.databind.json.JsonMapper`)을 주입받아 한다. 이 빈에는 Boot가 `ProblemDetail`의 `properties`(`code`, `errors`)를 최상위 필드로 펼치는 mixin을 넣어 두어, 컨트롤러 응답과 같은 JSON이 된다. `new JsonMapper()`로 따로 만들면 `properties`가 중첩 객체로 나간다.
- 10-5의 `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`, `ProblemDetailRequestRejectedHandler`(`global/security`)는 `ProblemDetailResponseWriter`로 응답한다.

**ProblemDetail이 아닌 응답**: 필터에서 처리되지 않은 예외처럼 컨트롤러 밖에서 생긴 오류는 서블릿 컨테이너가 `/error`로 보내고(ERROR 디스패치), Spring Boot `BasicErrorController`가 기본 형식(`timestamp`, `status`, `error`, `path`, 메시지·스택 트레이스 없음)으로 응답한다. 서버 버그라 프론트가 `code`로 분기할 대상이 아니고(프론트는 `code: null`로 처리, 11장), ProblemDetail로 맞추려면 `ErrorController`를 따로 구현해야 해서 기본 형식을 둔다. 클라이언트 입력으로 자주 생기는 방화벽 거절은 `/error`로 보내지 않고 ProblemDetail로 응답한다 (10-5).
- Tomcat이 Spring에 넘기기 전에 거절하는 요청(인코딩된 `/` `%2F` 등, 10-5)도 Tomcat 기본 오류 응답이 나간다. Spring 필터를 거치지 않아 `X-Request-Id`도 없다. 위와 같은 이유로 기본 형식을 둔다.

---

## 10. 인증·보안 설계

### 10-1. 토큰과 쿠키
| 쿠키 | 내용 | 속성 |
|---|---|---|
| `access_token` | JWT (15분) | HttpOnly, Secure, SameSite=Lax, Path=/api |
| `refresh_token` | 랜덤 문자열 (14일), DB에는 SHA-256 해시 저장 | HttpOnly, Secure, SameSite=Lax, Path=/api/v1/auth |
| `XSRF-TOKEN` | CSRF 토큰 | **HttpOnly 아님** (JS가 읽어서 헤더로 보냄), SameSite=Lax, Path=/, Secure는 HTTPS 요청일 때만 (10-5) |

- 토큰을 localStorage·sessionStorage에 저장하지 않는다.
- JWT 서명 키는 환경변수로만 주입한다 (`JWT_SECRET`, 256bit 이상). 형식과 검사는 10-6.
- `access_token`·`refresh_token`의 `Secure`는 설정 `blog.auth.secure-cookie`로 정한다. `application.yaml`에 `true`, `application-local.yaml`에만 `false`를 둔다 (test·prod는 `true`).
  - `XSRF-TOKEN`처럼 `request.isSecure()`를 따르지 않는 이유: 운영에서 Nginx의 `X-Forwarded-Proto`가 빠지면 인증 토큰이 `Secure` 없이 조용히 나간다. 인증 토큰은 설정이 빠져도 안전한 쪽(`Secure`)으로 둔다. `XSRF-TOKEN`은 값이 노출돼도 헤더를 직접 붙일 수 없는 다른 출처에는 쓸모가 없어 요청을 따른다 (10-5).
- 쿠키 이름·속성·`Max-Age`는 `CookieUtil`(`global/security`) 한 곳에서 만든다 (`ResponseCookie`, `Set-Cookie` 헤더). `Max-Age`는 토큰 수명과 같다 (Access 900초, Refresh 1209600초). 수명은 코드 상수로 두고 설정으로 빼지 않는다.

### 10-2. CSRF 보호
인증 정보가 **쿠키로 자동 전송**되므로 CSRF 보호를 끄지 않는다. (`csrf().disable()`은 토큰을 Authorization 헤더로 보낼 때만 안전하다.)

- Spring Security의 SPA용 CSRF 설정을 사용한다 (`CookieCsrfTokenRepository` + SPA용 `CsrfTokenRequestHandler`, Spring Security 7.0+에서는 `csrf.spa()`, 10-5).
- 흐름
  1. 프론트가 관리자 화면·로그인 화면 진입 시 `GET /api/v1/auth/csrf` 호출 → `XSRF-TOKEN` 쿠키 발급
  2. 상태 변경 요청(POST/PUT/DELETE) 시 쿠키 값을 읽어 `X-XSRF-TOKEN` 헤더로 전송
  3. 서버가 쿠키와 헤더 값을 비교
- CSRF 검사 제외: GET·HEAD·OPTIONS·TRACE 요청(Spring 기본값, 10-5), `POST /api/v1/posts/{slug}/views`(M6)
- `SameSite=Lax`는 추가 방어선으로 함께 유지한다.

### 10-3. 인증 흐름
```
1. 로그인 화면 진입 → GET /api/v1/auth/csrf
2. POST /api/v1/auth/login (X-XSRF-TOKEN 포함)
   → BCrypt 검증 → access_token, refresh_token 쿠키 발급, refresh 해시 DB 저장
3. 관리자 요청 → 쿠키 자동 전송
   → JwtAuthenticationFilter가 access_token 검증 → SecurityContext 등록
4. 401 (Access 만료) → 프론트가 POST /api/v1/auth/refresh 호출 후 원래 요청 1회 재시도
   → 재발급도 401이면 /admin/login으로 이동
5. 로그아웃 → refresh 해시 삭제, 쿠키 만료
```
- 동시에 여러 요청이 401을 받으면 refresh는 **한 번만** 호출하고 나머지는 그 결과를 기다리도록 프론트에서 처리한다.

### 10-4. 그 외 보안 규칙
- 로그인 API는 Nginx `limit_req`로 IP당 요청 수를 제한한다 (M3).
- 응답 헤더: `X-Content-Type-Options`, `X-Frame-Options`(또는 CSP `frame-ancestors`), `Strict-Transport-Security`를 Nginx에서 추가한다.
- Markdown 렌더링 시 raw HTML을 허용하지 않는다 (react-markdown 기본값 유지, `rehype-raw` 사용 금지).

---

### 10-5. Security 필터 설정
- 세션을 쓰지 않는다 (`SessionCreationPolicy.STATELESS`, `requestCache` 끔). `formLogin`, `httpBasic`, 기본 `logout`(`/logout`)은 끈다. 로그아웃은 8-3의 API로 한다.
- 경로 규칙 (위에서부터 적용)
  | 경로 | 규칙 |
  |---|---|
  | `/actuator/health/**`, Swagger(`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) | 모두 허용 (Swagger는 `local`에서만 켜짐, 5장. `/swagger-ui.html`은 springdoc의 진입 주소로 `/swagger-ui/index.html`로 이동시킨다) |
  | `/error` | 모두 허용 (아래 '`/error`') |
  | `GET /api/v1/posts/**`, `GET /api/v1/categories`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/login` | 모두 허용 (로그인도 CSRF 검사는 받는다) |
  | `/api/v1/admin/**` | `ROLE_ADMIN` |
  | 그 외 | 인증 필요 |
  - 재발급·로그아웃의 규칙은 그 API를 만드는 이슈(#70)에서 추가한다 (없는 경로를 미리 열어 두지 않는다). `GET /api/v1/auth/me`는 '그 외'(인증 필요)에 걸리므로 따로 적지 않는다.
  - `/error`: Spring Boot가 Security 필터를 ERROR 디스패치에도 등록하고 인가도 ERROR 디스패치에 적용되므로(기본값), 막으면 컨트롤러 밖 오류(필터의 처리되지 않은 예외 등)의 원래 상태(500 등)가 `401`로 바뀐다. 그래서 경로로 허용한다 (`dispatcherTypeMatchers`가 아니라 경로로 열어 MockMvc로 확인할 수 있게). 응답은 9장 'ProblemDetail이 아닌 응답'. `/api`로 시작하지 않아 외부(Nginx)에서 직접 닿지 않는다 (3장).
- CSRF: `csrf.spa()`에 SameSite를 붙인 `CookieCsrfTokenRepository`를 넣는다 (`spa()`는 쿠키 속성을 바꿀 수 없어 저장소만 교체).
  - `XSRF-TOKEN` 쿠키: HttpOnly 아님, `Path=/`, `SameSite=Lax`, `Secure`는 요청이 HTTPS인지(`request.isSecure()`)를 따른다. 운영은 Nginx 뒤에서 `forward-headers-strategy: framework`로 HTTPS가 되어 `Secure`가 붙고, local(http)은 붙지 않아 프로필 설정이 필요 없다.
  - 토큰 로딩: `csrf.spa()`의 요청 처리기는 모든 요청에서 토큰을 미리 불러온다(Spring Security 7.1.1 `SpaCsrfTokenRequestHandler`, SPA가 언제든 쿠키를 읽을 수 있게 하려는 설계). 그래서 쿠키가 없는 요청이면 CSRF 필터를 지나는 모든 응답(공개 GET, health, 401·403 포함)에 `Set-Cookie: XSRF-TOKEN`이 붙는다. 방화벽·Tomcat에서 거절된 요청은 CSRF 필터 전이라 붙지 않는다.
    - 이를 받아들인다. 세션을 쓰지 않아 서버 상태가 생기지 않고, 공개 API는 Next 서버 컴포넌트가 서버 간 호출로 부르므로 브라우저에 쿠키가 쌓이지 않는다(서버 측 `fetch`는 `Set-Cookie`를 저장하지 않는다). 막으려면 `spa()` 대신 요청 처리기를 직접 조립해야 해 코드가 늘고 Spring 권장 설정에서 벗어난다.
  - `GET /api/v1/auth/csrf`(`AuthController`)는 `CsrfToken`을 인자로 받아 `getToken()`을 호출해 토큰을 확정하고 `204`로 응답한다. 인자로 받은 `CsrfToken`은 지연 객체라 받기만 해서는 토큰이 만들어지지 않고, 값을 읽을 때 만들어져 쿠키로 저장된다. 위 미리 불러오기로 쿠키는 이미 정해지지만, `getToken()`을 직접 불러 이 진입점이 `spa()` 동작에 기대지 않게 한다. 진입점을 두는 이유는 8-3.
  - 검사 대상: GET·HEAD·OPTIONS·TRACE를 뺀 모든 요청 (Spring 기본값). 조회수 API 제외(10-2)는 M6에서 추가한다.
- 오류 응답 (Tomcat 거절 행을 빼고 모두 `application/problem+json`, 9장 형식, `instance`는 요청 경로)
  | 상황 | 응답 |
  |---|---|
  | 인증 없이 보호된 경로 | `401` `UNAUTHORIZED` (`AuthenticationEntryPoint`) |
  | CSRF 토큰이 없거나 쿠키와 헤더가 다름 (`CsrfException`) | `403` `INVALID_CSRF_TOKEN` (`AccessDeniedHandler`) |
  | 그 외 권한 없음 | `403` `FORBIDDEN` (`AccessDeniedHandler`) |
  | 요청 URL이 `StrictHttpFirewall` 기본 규칙에 걸림 (예: `GET /api/v1/posts/a;b`) | `400` `INVALID_INPUT`, `errors` `[]` (`RequestRejectedHandler`) |
  | Tomcat이 먼저 거절하는 URL (인코딩된 `/` `%2F`, Tomcat 기본 `encodedSolidusHandling=reject`) | Tomcat의 `400` (ProblemDetail 아님, 9장 'ProblemDetail이 아닌 응답') |
  - 클래스: `global/security`의 `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`(`exceptionHandling`에 등록), `ProblemDetailRequestRejectedHandler`(빈으로 등록하면 Spring Security가 가져다 쓴다). 응답은 9장의 `ProblemDetailResponseWriter`로 쓴다.
  - 방화벽 거절: 기본 `HttpStatusRequestRejectedHandler`는 `sendError(400)`이라 `/error`로 가서 ProblemDetail이 아닌 형식이 된다. 잘못된 클라이언트 요청이라 다른 400과 같은 `INVALID_INPUT`으로 맞춘다. 상태 코드는 Spring 기본값(`400`)을 유지한다.
  - Tomcat 거절: Tomcat 11의 커넥터 기본값이고 Spring Boot 4.1.1은 바꾸지 않는다. 설정을 바꾸지 않는다. 경로에 `/`가 인코딩되어 들어올 정상 요청이 없고(slug 규칙에 `/`가 없다), 프론트는 `isValidSlug`로 미리 걸러 API를 부르지 않는다(11장). `decode`로 바꾸면 경로가 달라지는 보안 위험이 생긴다.
  - MockMvc에는 Tomcat이 없어 `%2F`도 방화벽까지 가서 운영과 결과가 다르다. 테스트 예시는 `;`처럼 운영에서도 Spring까지 오는 값만 쓴다.
  - CSRF 필터가 인가보다 먼저 실행되므로, 인증 없이 CSRF 토큰도 없이 보낸 POST는 `401`이 아니라 `403 INVALID_CSRF_TOKEN`이다.
  - 관리자 계정이 하나뿐이라 로그인하면 항상 `ROLE_ADMIN`이다. `FORBIDDEN`은 권한이 다른 인증 주체(테스트의 `@WithMockUser(roles = "USER")`)에서만 나온다.
  - 로그는 남기지 않는다 (5장 로깅, 프레임워크가 만드는 4xx).
- 필터 순서: `RequestIdFilter`(`HIGHEST_PRECEDENCE`)가 Security 필터보다 먼저 실행되어 400·401·403 응답에도 `X-Request-Id`가 붙는다.
- `JwtAuthenticationFilter`(`OncePerRequestFilter`)는 빈으로 등록하지 않고 `SecurityConfig`에서 `new`로 만들어 `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`로 넣는다. `@Component`로 두면 Spring Boot가 서블릿 필터로도 자동 등록해 Security 체인 밖에서 한 번 더 실행된다. 동작은 10-6.

### 10-6. JWT와 로그인
**Access 토큰 (JWT, `JwtProvider`)**
- 라이브러리는 jjwt 0.13, 알고리즘은 `HS256`(`Jwts.SIG.HS256`을 명시. 키 길이에 따라 알고리즘이 바뀌지 않게).
- claim: `sub`(username), `iat`, `exp`(발급 + 15분). 권한 claim은 두지 않는다 (관리자 한 명이라 유효한 토큰이면 항상 `ROLE_ADMIN`, 10-5).
- 발급·검증 시각은 `Clock` 빈을 쓴다 (jjwt `clock`에 연결). 만료 판정은 jjwt 기본(허용 오차 0)을 따른다.
- 서명 키: `blog.auth.jwt-secret: ${JWT_SECRET:}`를 **Base64로 디코딩한 바이트**를 쓴다. 생성은 `openssl rand -base64 32`.
  - 문자열을 그대로(UTF-8) 키로 쓰지 않는 이유: 32글자 문자열은 길이 검사를 통과해도 사람이 만든 값이면 256bit 무작위가 아니다. Base64 디코딩 결과의 길이를 검사하면 무작위 바이트 생성을 강제하기 쉽다.
  - 검사는 `JwtProvider` 생성 시(기동 시) 한다. 어기면 `IllegalStateException`으로 기동을 멈추고, 메시지에는 설정 키 이름만 넣는다 (키 값 금지).
  - 판정 순서: `isBlank()` → `strip()` → `java.util.Base64.getDecoder()`(표준 Base64) → 디코딩 결과 길이. 디코더에 따라 달라지는 입력의 결과는 다음과 같다.
    | 입력 | 결과 |
    |---|---|
    | 앞뒤 공백·줄바꿈 (`.env`에 붙은 경우) | `strip()`으로 지우고 통과 |
    | 값 중간의 공백·줄바꿈 | Base64 아님 |
    | 패딩(`=`) 없는 값 (예: 32바이트 키의 43자 표기) | 통과 (표준 디코더는 패딩을 요구하지 않는다) |
    | `-`·`_`가 든 값 (URL-safe 표기) | Base64 아님 (표준 알파벳만 허용) |
  - `openssl rand -base64 32`의 출력(44자, 패딩 `=` 하나)이 그대로 통과해야 한다.
  | 값 | `jwt-secret-required: false` (local, test) | `true` (prod) |
  |---|---|---|
  | 비어 있음 (`isBlank()`) | `WARN` 한 줄 후 기동마다 무작위 32바이트 키 사용 (재시작하면 기존 토큰 무효) | `IllegalStateException` |
  | Base64가 아님 | `IllegalStateException` | 같음 |
  | 디코딩 결과 32바이트 미만 | `IllegalStateException` | 같음 |
  - local·test에서 비면 무작위 키를 쓰는 이유: 저장소가 Public이라 고정 개발용 키를 설정 파일에 둘 수 없고, 공개 화면 개발과 테스트는 고정 키가 필요 없다 (5장 '관리자 계정 생성'과 같은 기준).
- 설정 record: `global/config/AuthProperties` (`blog.auth`: `jwtSecret`, `jwtSecretRequired`, `secureCookie`). `application.yaml`은 `jwt-secret: ${JWT_SECRET:}`, `jwt-secret-required: false`, `secure-cookie: true`, `application-prod.yaml`은 `jwt-secret-required: true`, `application-local.yaml`은 `secure-cookie: false`. `AdminProperties`와 같은 이유로 `toString()`을 재정의해 `jwtSecret`을 가린다 (값이 있으면 `****`).

**`JwtAuthenticationFilter`**
| `access_token` 쿠키 | 결과 |
|---|---|
| 같은 이름이 여러 개 (Path가 다른 예전 쿠키가 남은 경우) | 요청에 실린 순서의 **첫 값만** 쓰고 나머지는 무시한다. 첫 값이 잘못됐으면 뒤 값이 유효해도 인증하지 않는다 (브라우저는 Path가 더 긴 쿠키를 먼저 보내므로 첫 값이 현재 발급분이다) |
| 없음, 또는 값이 비어 있음(`isBlank()`) | 인증 없이 다음 필터로 |
| 서명·형식이 맞고 만료 전 | `SecurityContext`에 인증 등록 (principal은 username 문자열, 권한 `ROLE_ADMIN`) |
| 만료, 서명 불일치, 형식 오류, 서명 없는 토큰(`alg: none`) | 인증 없이 다음 필터로 (jjwt `JwtException`을 잡는다) |
- 필터는 직접 응답하지 않는다. 보호된 경로면 인가 단계에서 `401` `UNAUTHORIZED`(10-5)가 되고, 공개 경로는 쿠키가 잘못돼도 그대로 열린다. 만료와 위조를 다른 코드로 나누지 않는다: 프론트는 어느 쪽이든 재발급을 한 번 시도하면 되고(10-3), 구분해 알려 주면 공격자에게만 정보가 된다.
- 로그를 남기지 않는다 (5장, Security 필터의 401).

**로그인 (`AuthService.login`)**
- `adminRepository.findByUsername(username)`(대소문자 구분, 정확히 같은 값)으로 찾는다.
- 아이디가 없을 때도 BCrypt 비교를 한 번 한다. 처음 한 번 만들어 둔 더미 해시(`passwordEncoder.encode`)와 비교해, 아이디 유무에 따라 응답 시간이 달라지지 않게 한다 (Spring Security `DaoAuthenticationProvider`의 `mitigateAgainstTimingAttack`과 같은 방식).
  - 정리하면 모든 경로에서 `passwordEncoder.matches`를 정확히 한 번 부르고, "관리자가 있음 && 비밀번호 72바이트 이하 && `matches`가 참"일 때만 성공이다. 그 외는 `BusinessException(INVALID_CREDENTIALS)`.
- `AuthenticationManager`·`UserDetailsService`를 쓰지 않고 서비스에서 직접 비교한다. 로그인 경로가 하나뿐이라 위 규칙을 서비스 코드와 Mockito 테스트로 드러내는 편이 단순하다.
- 성공하면 Access 토큰과 Refresh 토큰을 만들고 Refresh 해시를 저장한다 (`@Transactional`). 서비스는 `LoginResult(username, accessToken, refreshToken)`을 돌려주고, 쿠키는 컨트롤러가 `CookieUtil`로 붙인다.

**Refresh 토큰**
- 원문: `SecureRandom` 32바이트(256bit)를 Base64URL(패딩 없음, 43자)로 인코딩한 문자열. JWT가 아니다 (DB에서 찾아 무효화하는 토큰이라 자체 검증 정보가 필요 없다).
- 저장: 원문의 SHA-256을 소문자 16진수 64자로 `token_hash`에 저장한다 (6장, 원문 저장 금지). `expires_at`은 발급 + 14일(`Clock`), `created_at`은 `@CreatedDate`.
- `RefreshToken` 엔티티(`domain/auth/entity`): `admin`은 `@ManyToOne(fetch = LAZY)`, `refresh_token` 테이블에 `updated_at`이 없어 `BaseTimeEntity`를 상속하지 않는다 (`Admin`과 같다). 조회·삭제 쿼리는 #70에서 추가한다.

## 11. 프론트엔드 구조

**App Router 규칙 요약**
- 폴더 = URL 경로, `page.tsx` = 해당 경로의 화면, `layout.tsx` = 공유 레이아웃
- `[slug]` = 동적 경로, `(group)` = URL에 나타나지 않는 레이아웃 그룹
- Next.js 16부터 기존 `middleware.ts`는 `proxy.ts`로 이름이 바뀌었다. 설치 버전에 맞춰 사용한다.

```
frontend/src/
├ app/
│  ├ layout.tsx                     # 최상위 HTML, 폰트, 기본 metadata
│  ├ (public)/
│  │  ├ layout.tsx                  # 블로그 헤더 (푸터는 M5)
│  │  ├ not-found.tsx               # 공개 페이지의 notFound() 화면 (헤더 포함)
│  │  ├ page.tsx                    # /                  글 목록
│  │  ├ posts/[slug]/page.tsx       # /posts/{slug}      글 상세 + Giscus
│  │  ├ categories/[slug]/page.tsx  # /categories/{slug}
│  │  ├ tags/[name]/page.tsx        # /tags/{name}
│  │  └ search/page.tsx             # /search?q=
│  ├ admin/
│  │  ├ login/page.tsx              # /admin/login
│  │  └ (dashboard)/
│  │     ├ layout.tsx               # 관리자 레이아웃
│  │     ├ posts/page.tsx           # /admin/posts
│  │     ├ posts/new/page.tsx       # /admin/posts/new
│  │     ├ posts/[id]/edit/page.tsx # /admin/posts/{id}/edit
│  │     └ categories/page.tsx      # /admin/categories
│  ├ not-found.tsx                  # (M5) 어느 경로에도 맞지 않는 URL
│  ├ sitemap.ts
│  ├ robots.ts
│  └ rss.xml/route.ts
├ components/
│  ├ ui/            # Button, Input 등 공통
│  ├ post/          # PostCard, PostList, Pagination, TagBadge(M4)
│  ├ markdown/      # MarkdownRenderer (서버 컴포넌트)
│  └ admin/         # PostForm, MarkdownPreview
├ lib/
│  ├ api/
│  │  ├ server.ts   # 서버 컴포넌트용 fetch (API_INTERNAL_URL, server-only)
│  │  ├ client.ts   # (M2) 브라우저용 fetch (/api/v1, CSRF 헤더, 401 시 refresh 후 재시도)
│  │  ├ errors.ts   # ProblemDetail 파싱, ApiError
│  │  ├ types.ts    # (M1 임시) 응답 타입 수작업 정의, M2에서 api.d.ts로 교체 후 삭제
│  │  ├ posts.ts
│  │  └ auth.ts
│  ├ date.ts        # UTC → KST 표시 변환 (formatDate, YYYY.MM.DD)
│  ├ pagination.ts  # URL의 page 쿼리 해석 (parsePageParam)
│  ├ slug.ts        # slug 형식 검사 (isValidSlug, 6장 slug 규칙)
│  └ utils.ts
├ schemas/
│  └ post.ts        # Zod 스키마 (postSchema, categorySchema, loginSchema) - 서버 검증 규칙과 동일
├ types/
│  └ api.d.ts       # openapi-typescript 생성 파일 (직접 수정 금지)
└ proxy.ts          # /admin/** 접근 시 access_token 쿠키 없으면 /admin/login으로 이동
```

### 렌더링 전략
- **공개 페이지**: 서버 컴포넌트로 요청마다 렌더링하고, 아래 두 가지만 캐시한다. 공개 페이지의 캐시 기준은 이 항목에만 두고, 다른 곳은 여기를 가리킨다.
  - 캐시하는 것
    - 데이터 함수(`lib/api/posts.ts`): `'use cache'` + `cacheLife({ revalidate: 60 })`. `server.ts`에는 캐시를 넣지 않는다.
    - `MarkdownRenderer` 컴포넌트: `'use cache'` + `cacheLife('max')` (이유는 'Markdown 렌더링'). 컴포넌트 단위 캐시는 여기에만 둔다.
  - 캐시하지 않는 것: 페이지 전체. 응답은 빌드 때 만든 정적 셸(`Suspense` fallback 포함)과, 위 캐시로 그 자리에서 렌더링한 본문 스트리밍이다 (Next 16.4의 `next start`·standalone에서 확인). Partial Prefetching의 ISR 업그레이드는 `generateStaticParams`가 돌려준 `params`에만 적용되는데, 빌드 시 API 비의존 때문에 그 함수를 쓰지 않는다.
    - 본문은 같은 응답 안에서 스트리밍되어 검색엔진이 받는 HTML에 들어 있다. 매 요청 비용은 캐시된 결과로 컴포넌트를 렌더링하는 정도라 작은 서버에서도 충분하다고 본다. 운영에서 느리면 측정 후 다시 검토한다.
  - `next.config.ts`의 `cacheComponents: true`(create-next-app 기본값) 모드라 캐시 주기는 `export const revalidate`나 `fetch`의 `next.revalidate`가 아니라 `cacheLife`로 정한다. 구현 전 `node_modules/next/dist/docs/`의 caching 문서를 확인한다.
  - `'use cache'` 함수·컴포넌트를 요청 시점 API(`params`, `searchParams`, `connection()`) 없이 호출하면 빌드 때 실행되어 정적 셸에 들어간다(= 빌드 중 백엔드 호출). 반드시 요청 시점 API를 거친 뒤 호출한다.
- **관리자 페이지**: 클라이언트 컴포넌트, 브라우저에서 `/api/v1` 직접 호출.
- `proxy.ts`의 쿠키 체크는 화면 이동 편의용이다. **실제 권한 검증은 Spring Security가 담당한다.**
- 관리자가 글을 저장·수정·삭제해도 공개 페이지는 데이터 캐시 주기에 따라 최대 60초 후 반영된다(본문이 바뀌면 `MarkdownRenderer`의 캐시 키도 바뀐다). 즉시 반영(on-demand revalidate)은 M5 이후 검토한다.

### 빌드 시 API 의존 제거
CI에서 프론트 이미지를 빌드할 때는 백엔드가 없다. `next build`가 데이터 fetch를 하는 페이지를 미리 렌더링하면 빌드가 실패한다.
- 데이터를 가져오는 페이지(홈, 목록, 상세, sitemap, RSS)는 **빌드 시점에 프리렌더링하지 않고** 요청 시 렌더링한다 (캐시 기준은 '렌더링 전략'. Cache Components 모드에서는 요청 시점 데이터를 `Suspense` 안에서 읽는다).
- `generateStaticParams`는 사용하지 않는다.
- `next.config`에 `output: 'standalone'`을 설정해 Docker 이미지를 경량화한다.

### API 주소 처리
- 서버 컴포넌트: 환경변수 `API_INTERNAL_URL` (로컬 `http://localhost:8080`, 서버 `http://backend:8080`)
  - `NEXT_PUBLIC_`이 아니므로 빌드 때 박히지 않고 실행 시점에 읽힌다. 이미지 하나를 환경변수만 바꿔 쓴다.
  - 값이 없으면 기본값으로 대신하지 않고, 호출 시점에 명확한 오류를 낸다 (운영 설정 누락이 엉뚱한 주소 연결로 숨지 않게).
  - 로컬: `frontend/.env.example`(커밋, 값 `http://localhost:8080`)을 `.env.local`로 복사해 쓴다. 운영: `docker-compose.prod.yml`의 `environment`에 직접 적는다 (비밀값 아님).
  - `server.ts`는 `import 'server-only'`로 클라이언트 번들에 들어가지 않게 한다.
- 브라우저: 항상 상대 경로 `/api/v1` (M2부터. M1 공개 페이지는 서버 컴포넌트만 호출한다)
  - 로컬에서는 `next.config`의 `rewrites`로 `/api/*` → `http://localhost:8080/api/*` (M2, `client.ts`와 함께 추가)

### 에러 처리
- 2xx가 아닌 응답은 `ApiError`(`status`, `code`, `detail`, `errors`)로 바꾼다. 본문이 ProblemDetail이면 파싱하고, 아니면(Nginx 502 HTML 등) `code: null`과 일반 메시지를 넣는다.
- 네트워크 오류(백엔드 다운 등)는 감싸지 않고 그대로 던져 Next 오류 화면으로 보낸다.
- 페이지에서 `ApiError`의 `status === 404`면 `notFound()`를 호출한다.
  - 단, `'use cache'` 함수 안에서 던진 오류는 캐시 경계를 넘으며 일반 `Error`가 되어 `instanceof ApiError`와 `status`를 잃는다. 그래서 캐시하는 단건 조회 함수(`getPost`)는 404를 함수 안에서 `null`로 바꿔 돌려주고, 페이지는 `null`이면 `notFound()`를 호출한다. 그 외 오류는 그대로 던진다 (오류 화면으로 가므로 `status`가 없어도 된다).

### 날짜 표시
- KST `YYYY.MM.DD` (`2026.10.11`). 내장 `Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' })`의 `formatToParts`로 조립한다 (`timeZone`을 명시해 서버 시간대와 무관하게 동작).
- 날짜 라이브러리(dayjs, date-fns)는 쓰지 않는다. 상대 시간("3일 전"), 기간 계산, 문자열 파싱이 필요해지면 다시 검토한다.
- 잘못된 입력은 `RangeError`를 그대로 던진다 (API의 `publishedAt`은 PUBLISHED 글에서 항상 있음).

### 글 목록 페이지 (`/`)
- URL은 `/?page=N`이고, 사용자에게 보이는 값이라 `page`는 **1부터** 센다. API를 호출할 때 `page - 1`로 바꾼다.
  - 첫 페이지 링크는 `/`(파라미터 없음)로 한다. `?page=1`로 들어와도 첫 페이지를 보여 주고 리다이렉트하지 않는다.
  - 페이지 크기는 API 기본값(10)을 쓰고 URL로 받지 않는다.
- `page` 해석은 `lib/pagination.ts`의 순수 함수 `parsePageParam`이 한다. 값이 없으면 1, `^[1-9][0-9]*$`에 맞고 10000 이하면 그 수, 그 외(`0`, 음수, 소수, 문자, 10000 초과, 같은 키가 여러 개)는 잘못된 값이다.
  - 상한은 API `page` 범위(7장)에 맞춘 것이다. API를 호출하기 전에 걸러, 범위 밖 값이 API 400으로 오류 화면이 되지 않게 한다. API의 400을 `notFound()`로 바꾸지는 않는다 (프론트가 잘못된 파라미터를 보내는 버그가 404로 가려진다).
- 잘못된 `page`, 범위를 넘은 `page`(2 이상인데 `content`가 비어 있음)는 `notFound()`. 첫 페이지로 보정하지 않는다 (API와 같은 기준이고, 같은 내용이 여러 URL로 색인되지 않게).
  - `searchParams`를 `Suspense` 안에서 읽으므로 응답은 이미 스트리밍 중이라 상태 코드는 `200`으로 남고 Next가 `noindex`를 넣는다. 실제 `404` 상태가 필요하면 `proxy.ts`에서 미리 걸러야 하는데, 화면과 색인 결과가 같아 하지 않는다.
- 구성: `page.tsx`는 `Suspense`(fallback은 짧은 로딩 문구)만 두고, 그 안의 비동기 컴포넌트가 `searchParams`를 읽어 `getPosts(page)`(`lib/api/posts.ts`, `'use cache'` + `cacheLife({ revalidate: 60 })`)를 호출한다. 캐시는 인자(`page`)별로 나뉜다.
- `PostCard` 표시 항목: 제목(`/posts/{slug}` 링크), 요약(있을 때), 카테고리 이름(있을 때, 텍스트만. 카테고리 페이지는 M4), 발행일(`formatDate`).
  - 썸네일은 M1에서 표시하지 않는다 (업로드는 M6이고, 외부 URL은 `next/image` 허용 도메인 설정이 필요하다).
  - 태그는 M4 전까지 항상 `[]`이므로 표시하지 않는다.
- 페이지 이동(`Pagination`): "이전"·"다음" 링크와 현재 위치(`2 / 3`)만 둔다. "이전"은 `page > 1`, "다음"은 `hasNext`일 때 보이고, 전체가 한 페이지 이내면 영역을 숨긴다. 페이지 번호 목록은 글이 많아지면 검토한다.
- 글이 하나도 없으면(첫 페이지 `content`가 빔) "아직 작성된 글이 없습니다." 문구를 보여 준다.
- 레이아웃: `app/page.tsx`를 `app/(public)/page.tsx`로 옮기고, `(public)/layout.tsx`에 블로그 이름(`/` 링크) 헤더를 둔다. 푸터는 넣을 내용(RSS 링크 등)이 생기는 M5에서 추가한다.

### 글 상세 페이지 (`/posts/{slug}`)
- 구성: 목록 페이지와 같다. `page.tsx`는 `Suspense`만 두고, 그 안의 비동기 컴포넌트가 `params`를 읽어 `getPost(slug)`(`lib/api/posts.ts`, `'use cache'` + `cacheLife({ revalidate: 60 })`)를 호출한다.
- `getPost`를 호출하기 전에 `lib/slug.ts`의 순수 함수 `isValidSlug`로 6장 slug 규칙(`^[a-z0-9]+(?:-[a-z0-9]+)*$`, 200자 이하)을 검사하고, 맞지 않으면 `notFound()`.
  - 규칙에 맞는 값은 영문 소문자·숫자·하이픈뿐이라 그대로 API 경로에 넣는다. 규칙 밖 값을 그대로(또는 `encodeURIComponent`로) 넣으면 `.`이 든 값은 URL 정규화로 다른 API 경로가 될 수 있고(`encodeURIComponent`는 `.`을 인코딩하지 않는다), 인코딩된 슬래시(`%2F`)는 서버가 404가 아닌 400으로 거절할 수 있어 오류 화면이 된다.
  - `/posts/..`·`/posts/%2E%2E`는 브라우저가 요청 전에 `/`로 정규화하므로 상세 페이지에 오지 않는다(목록 페이지가 열린다). 검사는 값이 어떤 경로로 들어왔는지와 무관하게 `params`에 온 값에 적용한다.
  - 8-1의 "형식이 다른 slug는 404"와 결과가 같다 (API는 형식을 검사하지 않고, 프론트는 호출 전에 걸러 같은 not-found를 보여 준다). Next가 `params`의 `%2F`·`%2E`를 디코딩하든 안 하든 `%`·`/`·`.`은 규칙에 맞지 않으므로 결과가 같다.
  - 관리자 폼의 Zod 스키마(M2)도 이 함수의 패턴을 함께 쓴다.
- `getPost`는 404(없는 글, DRAFT)면 `null`을 돌려주고('에러 처리'), 페이지는 `null`이면 `notFound()`. 그 외 오류는 그대로 던진다.
  - 목록 페이지와 같은 이유로 상태 코드는 `200`으로 남고 `noindex`가 붙는다.
- 표시 항목: 제목(`h1`), 카테고리 이름(있을 때, 텍스트만)과 발행일(`formatDate`)을 `PostCard`와 같은 형식으로, 본문(`MarkdownRenderer`).
  - 요약·썸네일·태그·수정일은 표시하지 않는다. 요약은 목록과 M5의 OG 설명용이고, 썸네일·태그는 목록 페이지와 같은 이유다.
  - 글별 `<title>`·OG 태그(`generateMetadata`)는 M5에서 추가한다. Giscus도 M5.
- 캐시: 기준은 '렌더링 전략'. 같은 글을 다시 요청하면 `getPost`와 `MarkdownRenderer`의 캐시로 백엔드 호출과 Shiki 하이라이팅이 다시 일어나지 않는다.
- not-found 화면: `app/(public)/not-found.tsx`를 둔다. 공개 페이지에서 부른 `notFound()`(상세의 없는 글, 목록의 잘못된 `page`)가 블로그 헤더 안에서 "페이지를 찾을 수 없습니다." 문구와 `/` 링크로 표시된다.
  - 어느 경로에도 맞지 않는 URL(`/foo`)은 루트 `app/not-found.tsx`가 맡는데, 루트 레이아웃만 거쳐 헤더가 없다. M5의 not-found 페이지 작업에서 함께 정하고, 그 전까지는 Next 기본 404 화면을 쓴다.

### Markdown 렌더링
- `components/markdown/MarkdownRenderer`는 `content`(Markdown 문자열)만 받는 **서버 컴포넌트**다. react-markdown의 `MarkdownAsync`를 쓴다.
  - `@shikijs/rehype`는 하이라이터를 비동기로 만드는 플러그인이라 동기 컴포넌트(`Markdown`)에서는 쓸 수 없다. 서버에서 렌더링하므로 Shiki와 문법 파일이 브라우저 번들에 들어가지 않는다.
- 플러그인: `remarkPlugins: [remarkGfm]`(표, 체크리스트, 취소선), `rehypePlugins: [rehypeSlug, [rehypeShiki, 옵션]]`.
- Shiki 옵션
  - `themes: { light: 'github-light', dark: 'github-dark' }`. 사이트가 `prefers-color-scheme`으로 다크 모드를 따르므로, `globals.css`에서 같은 미디어 쿼리로 `--shiki-dark`, `--shiki-dark-bg` 변수 색으로 바꾼다.
  - `langs: []` + `lazy: true`: 코드 블록에 나온 언어만 처음 쓸 때 불러온다. 기본값은 내장 언어 전체를 한 번에 불러와 작은 서버의 기동 후 첫 렌더링 시간과 메모리를 쓴다. 언어 목록을 따로 관리하지 않아도 된다.
  - `defaultLanguage: 'text'`, `fallbackLanguage: 'text'`: 언어를 안 적었거나 모르는 언어(오타 등)여도 오류 없이 같은 코드 블록 모양으로 표시한다.
- 보안: raw HTML은 react-markdown 기본 동작(이스케이프해서 글자로 표시)을 유지한다. `rehype-raw`, `skipHtml` 변경, `urlTransform` 재정의는 하지 않는다 (기본 `urlTransform`이 `javascript:` 링크를 막는다).
- 스타일: `@tailwindcss/typography`를 쓴다 (Tailwind preflight가 제목·목록·표 기본 스타일을 지우므로). 설정은 `globals.css`의 `@plugin "@tailwindcss/typography";`.
  - `MarkdownRenderer`가 결과를 `<div className="prose dark:prose-invert max-w-none">`로 감싸서 반환한다. react-markdown에는 `className` prop이 없고, 본문 스타일은 렌더러가 책임져야 호출하는 페이지마다 같은 클래스를 반복하지 않는다.
  - `max-w-none`으로 `prose`의 기본 최대 너비(65ch)를 끄고, 본문 너비는 호출하는 페이지의 레이아웃이 정한다.
- 이미지·링크는 기본 `<img>`, `<a>`로 둔다. `next/image` 변환, 외부 링크 새 탭 열기, 제목 앵커 링크·목차는 필요해지면 검토한다 (`rehype-slug`의 `id`가 그 기반이다).
- 확인용 데이터: `LocalDataInitializer`의 시드 글에 raw HTML(`<script>`, `<img onerror>`)과 `javascript:` 링크, 언어 없는·모르는 언어 코드 블록을 넣어, 로컬에서 표·체크리스트·하이라이팅과 함께 안전하게 표시되는지 본다.
- 캐시(`'use cache'` + `cacheLife('max')`, 정의는 '렌더링 전략')를 두는 이유
  - 상세 페이지는 매 요청 렌더링되므로('렌더링 전략'), 캐시하지 않으면 요청마다 Shiki가 실행된다. 또 Shiki는 토큰화할 때 `Date.now()`를 불러, 캐시 밖에서 실행되면 Next가 요청마다 `encountered the unstable value Date.now()` 로그를 남긴다. `connection()`으로 요청 시점 렌더링을 확정하면 로그는 없어지지만 요청마다 Shiki가 실행된다.
  - 결과가 인자 `content`에만 달려 있고 인자가 캐시 키가 되므로, 글을 고치면 새 키가 된다. 같은 본문을 다시 하이라이팅할 이유가 없어 가장 긴 프로필(`max`)을 쓴다. 글 수정 반영 주기는 `getPost`의 60초가 정한다.
- `MarkdownRenderer`는 async 서버 컴포넌트라 단위 테스트하지 않는다. 렌더링 결과는 글 상세 페이지에서 시드 글로 확인한다.

### 프론트엔드 테스트
- Vitest로 `lib/`의 로직이 있는 순수 함수만 단위 테스트한다 (`date.ts`, `errors.ts`의 파싱 함수, `pagination.ts`, `slug.ts`).
  - `async` 서버 컴포넌트는 Vitest·Jest 모두 단위 테스트를 지원하지 않으므로 단위 테스트하지 않는다.
  - `server.ts`는 `server-only`가 테스트 환경에서 import 시 오류를 던지므로 단위 테스트하지 않는다 (실제 호출은 화면에서 확인).
- 최소 구성: `vitest`, `vitest.config.mts`(`resolve: { tsconfigPaths: true }`로 `@/` 별칭 해석, Vite 내장 기능이라 `vite-tsconfig-paths` 플러그인은 쓰지 않는다), `"test": "vitest run"`. React Testing Library·jsdom은 컴포넌트 테스트가 필요해질 때 추가한다.
- `vitest`는 정확한 버전으로 고정한다(`^` 없음). Vitest는 메이저가 7~9개월마다 나와 깨지는 변경이 있으므로, 메이저 업그레이드는 별도 이슈로 한다.

### 스크립트
```json
"scripts": {
  "gen:api": "openapi-typescript http://localhost:8080/v3/api-docs -o src/types/api.d.ts"
}
```

---

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
  - `client_max_body_size` 설정 (글 본문 크기 고려)
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

## 13. 마일스톤

| 단계 | 내용 | 완료 기준 |
|---|---|---|
| **M0. 세팅** | 모노레포, Spring/Next 초기화, 로컬 PostgreSQL(Docker), Flyway V1, ProblemDetail 기반 공통 예외 처리, Swagger(local), Actuator, Testcontainers 테스트 기반, CI 워크플로, 브랜치 보호 | 로컬에서 양쪽 실행, `/actuator/health` UP, CI 통과 |
| **M1. 글 조회** | Post·Category 엔티티, 공개 목록/상세 API, Next 목록/상세 페이지, Markdown + Shiki 렌더링, UTC→KST 표시, 빌드 시 API 비의존 설정 | 시드 글이 코드 하이라이팅과 함께 표시, API 없이 `next build` 성공 |
| **M2. 관리자** | AdminInitializer, Security + JWT + CSRF, 로그인/재발급/로그아웃/me, 관리자 글·카테고리 CRUD, 관리자 화면, PostForm(react-hook-form + Zod), openapi-typescript 연동 | 로그인 후 글 작성·수정·삭제 가능, CSRF 토큰 없는 요청은 403 |
| **M3. 배포** | Dockerfile(backend, frontend standalone), prod compose(로그 제한, healthcheck), Nginx 리버스 프록시·보안 헤더·rate limit, Certbot HTTPS, 도메인 연결, DB 백업, deploy 워크플로 | `https://blog.도메인` 접속, main 머지 시 자동 배포, 백업 파일이 Object Storage에 생성되고 복구 테스트 완료 |
| **M4. 분류/검색** | 태그(PostTag, 정규화), QueryDSL 검색·필터, 카테고리·태그·검색 페이지 | 카테고리·태그·키워드로 글 탐색 |
| **M5. 부가 기능** | Giscus, sitemap/robots/RSS, 글별 metadata(OG 태그), not-found 페이지, Umami, Uptime Kuma | 검색엔진 등록 가능, 댓글 동작, 장애 알림 수신 |
| **M6. 고도화** | 조회수(별도 API, 중복 방지), 이미지 업로드(Object Storage), Redis(캐시·Refresh 토큰), on-demand revalidate, 무중단 배포, 테스트 보강 | 선택 |

**원칙**
- 테스트는 M1부터 기능과 함께 작성한다. 계층별로 분리한다: Repository는 `@DataJpaTest` + Testcontainers, Service는 Mockito 단위 테스트(분기·예외가 있을 때), Controller는 `@WebMvcTest`, 인증·권한 흐름은 `@SpringBootTest` 통합 테스트.
- M0 → M3까지 먼저 완료해 배포된 상태를 만든 뒤 기능을 확장한다.
- 각 마일스톤은 GitHub 이슈 여러 개로 나누고, 이슈 하나 = PR 하나를 기본으로 한다.

---

## 14. 변경 이력

- **v2**
  - API base path를 `/api/v1`로 변경
  - 에러 응답을 ProblemDetail(RFC 9457) + `code`, `errors` 확장 형식으로 변경
  - 쿠키 기반 JWT에 맞춰 CSRF 보호 활성화 (SPA용 쿠키 CSRF)
  - 관리자 계정 생성을 Flyway 시드에서 환경변수 기반 `AdminInitializer`로 변경
  - 시간 타입을 `TIMESTAMPTZ` / `Instant` / ISO-8601 UTC로 통일
  - 조회수를 상세 조회 API에서 분리 (`POST /posts/{slug}/views`, M6)
  - Next.js 빌드 시 API 비의존 설정, `output: 'standalone'`, `middleware.ts` → `proxy.ts`
  - slug·태그 정규화 및 검증 규칙 추가
  - M3에 DB 백업, rate limiting, 보안 헤더, 로그 용량 제한, 롤백 추가
  - 인덱스, FK 삭제 정책, 상태 코드·ErrorCode 목록, API 요청·응답 예시 추가
- **v2.1**
  - 페이지네이션 범위 초과 시 `400`, `sort` 파라미터 미지원 (7장)
  - 목록·검색 쿼리 파라미터를 요청 record로 받는 규칙, 목록 정렬은 Repository에 고정 (5장)
  - 글 목록 보조 정렬(`id`), 카테고리 없는 글의 응답, M4 전 `tags` 처리 명시 (8-1)
  - 성공 응답에 공통 래퍼를 쓰지 않는다는 결정 명시 (7장)

- **v2.2**
  - 로깅 규칙 추가: 예외 로그는 `GlobalExceptionHandler` 한 곳, 4xx `WARN`·5xx `ERROR`, 요청 추적 ID(`RequestIdFilter`, `X-Request-Id`), 출력·SQL 로그·민감정보 기준 (5장)
  - Nginx가 `X-Request-Id`를 넘기고 접근 로그에 포함 (12-4)

- **v2.3**
  - 글 상세 경로 `slug`는 형식 검사 없이 조회하고 맞지 않으면 `404` (8-1)
  - 조회수 증가는 직접 UPDATE 쿼리로 해 `updatedAt`을 바꾸지 않음 (8-2)

- **v2.4**
  - 카테고리 목록에 글 0개 카테고리 포함(관리자 화면 공용), `postCount`는 PUBLISHED 기준, `name` 오름차순 정렬, 배열 응답 (8-1)

- **v2.5**
  - 로컬 시드는 Flyway가 아니라 `LocalDataInitializer`(`@Profile("local")`)로 엔티티를 통해 저장 (5장, 12-1)
  - DB 테스트는 Support 클래스의 `@ActiveProfiles("test")`로 실행, 테스트 프로필은 `test` 하나 (5장)

- **v2.6**
  - 프론트 API 기반: `API_INTERNAL_URL` 필수(누락 시 오류)·`.env.example` 복사, `server-only`, `rewrites`는 M2로, 캐시는 데이터 함수에 두고 요청 시점 API 뒤에서 호출 (11장)
  - 프론트 에러 처리(`ApiError`), 날짜 표시(KST `YYYY.MM.DD`, `Intl`, 라이브러리 없음) (11장)
  - 프론트 테스트: Vitest로 `lib/` 순수 함수만, 정확한 버전 고정, CI에 test 단계 (1장, 11장, 12-6)

- **v2.7**
  - 400 `errors[].reason`에 프레임워크 내부 메시지를 내보내지 않고, 타입 변환 실패는 일반 메시지로 바꿈 (9장)

- **v2.8**
  - API `page` 상한 9999 (7장)
  - 글 목록 페이지: `?page`는 1부터(상한 10000), 잘못되거나 범위를 넘은 값은 `notFound()`, `PostCard` 표시 항목, 이전·다음 이동, 빈 목록 문구, `(public)` 레이아웃 헤더 (11장)

- **v2.9**
  - Markdown 렌더링: 서버 컴포넌트 `MarkdownAsync`, Shiki 라이트·다크 테마와 언어 지연 로딩·대체 언어, raw HTML 기본 이스케이프 유지, `@tailwindcss/typography`(렌더러가 `prose` 요소로 감쌈), 시드 글로 확인 (1장, 11장)

- **v2.10**
  - 글 상세 페이지: API 호출 전 `isValidSlug`(6장 slug 규칙)로 걸러 `notFound()`, 404는 `getPost`가 `null`로 바꿔 `notFound()`(`'use cache'` 경계에서 `ApiError`가 일반 `Error`가 되므로), 표시 항목, `(public)/not-found.tsx`(루트 not-found는 M5) (11장)
  - 공개 페이지 캐시 기준을 '렌더링 전략' 한 곳에 정리: 데이터 함수(60초)와 `MarkdownRenderer`(`cacheLife('max')`)만 캐시하고 페이지 전체는 캐시하지 않음(정적 셸 + 본문 스트리밍, `generateStaticParams`를 쓰지 않아 ISR 업그레이드 미적용). 다른 곳은 이를 가리키도록 정리 (1장, 8-1, 11장)

- **v2.11**
  - 페이지네이션 숫자 변환은 Spring 기본 동작(빈 값·공백은 기본값, 관대한 숫자 형식 허용, 같은 키는 첫 값)을 따르고 바뀐 값에 범위 검사, 주요 동작은 테스트로 고정 (7장)

- **v2.12**
  - `csrf.spa()`는 Spring Security 7.0부터 (10-2)
  - Security 필터 설정: 세션 없음, 경로 규칙(Swagger `/swagger-ui.html`, `/error` 허용), `csrf.spa()` + SameSite 쿠키 저장소, 401·403 오류 응답 구분(CSRF는 `INVALID_CSRF_TOKEN`), 방화벽 거절은 `400` `INVALID_INPUT`·Tomcat이 먼저 거절하는 `%2F`는 Tomcat 기본 `400`(8-1 slug 404의 예외), Security 401·403·방화벽 400은 로그 없음 (5장, 8-1, 9장, 10-5)
  - `GET /api/v1/auth/csrf`: `204`, 쿠키가 없을 때만 `XSRF-TOKEN` 발급, 명시적 진입점으로 두는 이유, 컨트롤러가 `CsrfToken.getToken()`으로 토큰 확정 (7장, 8-3, 10-5)
  - `csrf.spa()`는 모든 요청에서 토큰을 미리 불러와 쿠키 없는 요청의 응답마다 `XSRF-TOKEN`이 붙는 것을 받아들임 (10-5)
  - `XSRF-TOKEN` 쿠키 속성(`Path=/`, HTTPS 요청일 때만 `Secure`)과 CSRF 검사 제외 메서드를 10-5에 맞춤, `Secure` 프로필 설정은 access·refresh 쿠키만(#69) (10-1, 10-2)
  - ProblemDetail 생성 코드를 `ProblemDetailFactory`·`ProblemDetailResponseWriter`(`global/exception`)로 공유, 직렬화는 Boot `JsonMapper` 빈, `/error`(ERROR 디스패치)는 Boot 기본 형식 (5장, 9장)

- **v2.13**
  - 관리자 계정 생성: `blog.admin.*` 설정(`global/config/AdminProperties`), 두 값이 모두 비면 prod는 기동 실패·그 외는 건너뜀, 한쪽만 있으면 항상 기동 실패, username(50자, 앞뒤 공백 없음)·password(12자 이상, 72바이트 이하) 검증, `AdminProperties.toString()`에서 비밀번호 마스킹, `PasswordEncoderConfig` 분리, `Admin`은 `createdAt`만, 비밀번호 변경은 행 삭제 후 재시작 (5장, 12-2)

- **v2.14**
  - 저장소 구조에 `docs/verification.md`(검증 세션 규칙) 추가 (4장)

- **v2.15**
  - 로그인·`me`: 요청 검증(username `@NotBlank`·50자, password `@NotEmpty`·공백만 허용), 72바이트 초과 비밀번호는 `401`, username 정확히 비교, 같은 키는 마지막 값, 재로그인 시 기존 Refresh 유지, `me`는 토큰의 `sub`만 사용 (8-3)
  - 로그인 실패 로그는 `GlobalExceptionHandler`의 `WARN`만, 입력 username은 남기지 않음 (5장)
  - access·refresh 쿠키 `Secure`는 `blog.auth.secure-cookie`(local만 `false`), 쿠키는 `CookieUtil`에서 생성 (10-1)
  - JWT: HS256, `sub`·`iat`·`exp`, 키는 Base64 디코딩 32바이트 이상·기동 시 검사(local·test는 비면 무작위 키), `AuthProperties`, 필터는 잘못된 토큰을 무시하고 인가 단계의 `401`에 맡김, 아이디가 없어도 더미 해시로 BCrypt 비교, Refresh 토큰 생성·해시 방식 (10-5, 10-6, 12-2)
  - Security 경로 규칙에 `POST /api/v1/auth/login` 허용 추가, `JwtAuthenticationFilter`는 빈이 아니게 등록 (10-5)
  - JWT 키 판정은 `strip()` 후 표준 Base64 디코더, `access_token` 쿠키가 여러 개면 첫 값만 사용 (10-6)
