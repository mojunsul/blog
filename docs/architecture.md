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
| Next.js 16 (App Router) + TypeScript | SSR/ISR 기반 블로그 화면, SEO |
| Tailwind CSS | 스타일링 |
| react-markdown + remark-gfm + rehype-slug + Shiki(`@shikijs/rehype`) | Markdown 렌더링, 코드 하이라이팅 |
| react-hook-form + Zod (`@hookform/resolvers`) | 관리자 폼 처리·검증 |
| openapi-typescript | 백엔드 OpenAPI 명세에서 TS 타입 자동 생성 |

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
│  └ architecture.md             # 이 문서
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
│  ├ config/          # QuerydslConfig, JpaAuditingConfig, OpenApiConfig, ClockConfig
│  ├ security/        # SecurityConfig, JwtProvider, JwtAuthenticationFilter, CookieUtil
│  ├ exception/       # ErrorCode, BusinessException, GlobalExceptionHandler
│  ├ init/            # AdminInitializer (ApplicationRunner)
│  └ common/          # BaseTimeEntity, PageResponse
└ domain/
   ├ post/
   │  ├ controller/   # PostController(공개), AdminPostController(관리자)
   │  ├ service/      # PostService
   │  ├ repository/   # PostRepository, PostQueryRepository(QueryDSL)
   │  ├ entity/       # Post, PostStatus
   │  └ dto/          # PostCreateRequest, PostUpdateRequest, PostSummaryResponse, PostDetailResponse ...
   ├ category/
   ├ tag/             # Tag, PostTag
   └ auth/            # AuthController, AuthService, Admin, RefreshToken

backend/src/main/resources/
├ application.yaml          # 공통
├ application-local.yaml    # 로컬 DB, Swagger 활성화
├ application-prod.yaml     # 운영 (값은 환경변수 주입, Swagger 비활성화)
└ db/migration/
   └ V1__init_schema.sql

backend/src/test/java/io/github/mojunsul/blog/
├ support/
│  ├ TestContainers.java               # PostgreSQL 컨테이너 하나를 모든 테스트가 공유
│  ├ RepositoryTestSupport.java        # @DataJpaTest + Testcontainers
│  └ IntegrationTestSupport.java       # @SpringBootTest + Testcontainers
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

**관리자 계정 생성**
- 회원가입 API는 만들지 않는다.
- `AdminInitializer`(`ApplicationRunner`)가 앱 시작 시 관리자 계정이 없으면 환경변수 `ADMIN_USERNAME`, `ADMIN_PASSWORD`로 생성한다 (BCrypt).
- 이미 존재하면 아무것도 하지 않는다.

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
- **필드 이름**: camelCase
- **시간**: ISO-8601 UTC (`"2026-10-11T01:00:00Z"`)
- **페이지네이션**: `page`(0부터), `size`(기본 10, 최대 50)
- **식별자**: 공개 API는 `slug`, 관리자 API는 `id`
- **수정**: `PUT`은 전체 교체 (작성과 같은 본문을 보냄)

### 상태 코드
| 코드 | 언제 |
|---|---|
| `200 OK` | 조회·수정 성공 |
| `201 Created` | 생성 성공 (`Location` 헤더 포함) |
| `204 No Content` | 삭제, 로그아웃, 재발급 성공 |
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
- 정렬: `publishedAt` 내림차순.

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
- 조회수는 이 API에서 증가시키지 않는다 (ISR 캐시 때문에 실제 방문 수와 맞지 않고, GET이 상태를 바꾸면 안 되기 때문).

#### GET `/api/v1/categories`
```json
[
  { "id": 1, "name": "Backend", "slug": "backend", "postCount": 8 },
  { "id": 2, "name": "Infra", "slug": "infra", "postCount": 3 }
]
```

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

### 8-3. 인증 API

| Method | URL | 설명 |
|---|---|---|
| GET | `/api/v1/auth/csrf` | CSRF 토큰 쿠키 발급 |
| POST | `/api/v1/auth/login` | 로그인 |
| POST | `/api/v1/auth/refresh` | Access 재발급 + Refresh Rotation |
| POST | `/api/v1/auth/logout` | 로그아웃 |
| GET | `/api/v1/auth/me` | 로그인 상태 확인 |

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
- 실패: `401` (`INVALID_CREDENTIALS`). 아이디·비밀번호 중 무엇이 틀렸는지 구분하지 않는다.

#### POST `/api/v1/auth/refresh`
- 본문 없음. `refresh_token` 쿠키로 인증.
- 성공: 새 `access_token`, 새 `refresh_token` 발급, 기존 Refresh 토큰 삭제, `204`
- 만료·위조·이미 사용된 토큰: `401` (`INVALID_REFRESH_TOKEN`). 이미 사용된 토큰이 다시 들어오면 탈취로 간주하고 해당 관리자의 Refresh 토큰을 모두 삭제한다.

#### POST `/api/v1/auth/logout`
- DB의 Refresh 토큰 삭제, 두 쿠키 `Max-Age=0`, `204`

#### GET `/api/v1/auth/me`
- 로그인 상태: `200` + `{ "username": "admin" }`, 아니면 `401`

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

> Spring Security 필터 단계에서 발생하는 401/403도 같은 형식으로 응답하도록 `AuthenticationEntryPoint`, `AccessDeniedHandler`를 구현한다.

---

## 10. 인증·보안 설계

### 10-1. 토큰과 쿠키
| 쿠키 | 내용 | 속성 |
|---|---|---|
| `access_token` | JWT (15분) | HttpOnly, Secure, SameSite=Lax, Path=/api |
| `refresh_token` | 랜덤 문자열 (14일), DB에는 SHA-256 해시 저장 | HttpOnly, Secure, SameSite=Lax, Path=/api/v1/auth |
| `XSRF-TOKEN` | CSRF 토큰 | **HttpOnly 아님** (JS가 읽어서 헤더로 보냄), Secure, SameSite=Lax |

- 토큰을 localStorage·sessionStorage에 저장하지 않는다.
- JWT 서명 키는 환경변수로만 주입한다 (`JWT_SECRET`, 256bit 이상).
- 로컬(http) 개발 시에만 `Secure` 속성을 끌 수 있도록 프로필별로 설정한다.

### 10-2. CSRF 보호
인증 정보가 **쿠키로 자동 전송**되므로 CSRF 보호를 끄지 않는다. (`csrf().disable()`은 토큰을 Authorization 헤더로 보낼 때만 안전하다.)

- Spring Security의 SPA용 CSRF 설정을 사용한다 (`CookieCsrfTokenRepository` + SPA용 `CsrfTokenRequestHandler`, Spring Security 6.4+에서는 `csrf.spa()`).
- 흐름
  1. 프론트가 관리자 화면·로그인 화면 진입 시 `GET /api/v1/auth/csrf` 호출 → `XSRF-TOKEN` 쿠키 발급
  2. 상태 변경 요청(POST/PUT/DELETE) 시 쿠키 값을 읽어 `X-XSRF-TOKEN` 헤더로 전송
  3. 서버가 쿠키와 헤더 값을 비교
- CSRF 검사 제외: GET 요청, `POST /api/v1/posts/{slug}/views`
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
│  │  ├ layout.tsx                  # 블로그 헤더/푸터
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
│  ├ not-found.tsx
│  ├ sitemap.ts
│  ├ robots.ts
│  └ rss.xml/route.ts
├ components/
│  ├ ui/            # Button, Input 등 공통
│  ├ post/          # PostCard, PostList, TagBadge
│  ├ markdown/      # MarkdownRenderer
│  └ admin/         # PostForm, MarkdownPreview
├ lib/
│  ├ api/
│  │  ├ server.ts   # 서버 컴포넌트용 fetch (API_INTERNAL_URL)
│  │  ├ client.ts   # 브라우저용 fetch (/api/v1, CSRF 헤더, 401 시 refresh 후 재시도)
│  │  ├ errors.ts   # ProblemDetail 파싱, ApiError
│  │  ├ posts.ts
│  │  └ auth.ts
│  ├ date.ts        # UTC → KST 표시 변환
│  └ utils.ts
├ schemas/
│  └ post.ts        # Zod 스키마 (postSchema, categorySchema, loginSchema) - 서버 검증 규칙과 동일
├ types/
│  └ api.d.ts       # openapi-typescript 생성 파일 (직접 수정 금지)
└ proxy.ts          # /admin/** 접근 시 access_token 쿠키 없으면 /admin/login으로 이동
```

### 렌더링 전략
- **공개 페이지**: 서버 컴포넌트 + ISR (`revalidate: 60`).
  - `next.config.ts`에 `cacheComponents: true`가 켜져 있다 (create-next-app 기본값). 이 모드에서는 `export const revalidate`와 `fetch`의 `next.revalidate`로 ISR을 지정하지 않고, 데이터 함수에 `'use cache'` + `cacheLife({ revalidate: 60 })`를 쓴다. 구현 전 `node_modules/next/dist/docs/`의 caching 문서를 확인한다.
- **관리자 페이지**: 클라이언트 컴포넌트, 브라우저에서 `/api/v1` 직접 호출.
- `proxy.ts`의 쿠키 체크는 화면 이동 편의용이다. **실제 권한 검증은 Spring Security가 담당한다.**
- 관리자가 글을 저장·수정·삭제해도 공개 페이지는 최대 60초 후 반영된다. 즉시 반영(on-demand revalidate)은 M5 이후 검토한다.

### 빌드 시 API 의존 제거
CI에서 프론트 이미지를 빌드할 때는 백엔드가 없다. `next build`가 데이터 fetch를 하는 페이지를 미리 렌더링하면 빌드가 실패한다.
- 데이터를 가져오는 페이지(홈, 목록, 상세, sitemap, RSS)는 **빌드 시점에 프리렌더링하지 않고** 첫 요청 시 렌더링한 뒤 캐시되도록 설정한다 (Cache Components 모드에서는 요청 시점 데이터를 `Suspense` 안에서 읽는다).
- `generateStaticParams`는 사용하지 않는다.
- `next.config`에 `output: 'standalone'`을 설정해 Docker 이미지를 경량화한다.

### API 주소 처리
- 서버 컴포넌트: 환경변수 `API_INTERNAL_URL` (로컬 `http://localhost:8080`, 서버 `http://backend:8080`)
- 브라우저: 항상 상대 경로 `/api/v1`
  - 로컬에서는 `next.config`의 `rewrites`로 `/api/*` → `http://localhost:8080/api/*`

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
- Spring Boot(IDE, `local` 프로필), Next.js(`npm run dev`)는 직접 실행

### 12-2. 서버 (OCI)
- `docker-compose.prod.yml`: nginx, certbot, frontend, backend, postgres, backup
- PostgreSQL 데이터는 named volume으로 영속화
- 비밀값은 서버의 `infra/.env`에만 둔다 (원본은 GitHub Secrets에서 관리)
  - `POSTGRES_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, Object Storage 인증 정보 등
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
  - 프록시 헤더: `Host`, `X-Real-IP`, `X-Forwarded-For`, `X-Forwarded-Proto`
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
 └ frontend-ci: lint, typecheck, build (백엔드 없이 성공해야 함)

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