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
- 이미 로그인한 상태에서 다시 로그인해도 새 토큰을 발급하고 기존 Refresh 토큰은 그대로 둔다 (다른 기기의 로그인 유지). 만료된 행 정리는 10-6 '만료 정리'.

#### POST `/api/v1/auth/refresh`
- 요청 본문 없음 (헤더 `X-XSRF-TOKEN` 필요). `refresh_token` 쿠키로 인증한다. 같은 이름의 쿠키가 여러 개면 첫 값만 쓴다 (10-6).
- 성공 `204`: 새 `access_token`·`refresh_token` 쿠키를 로그인과 같은 속성으로 발급하고 (10-1), 기존 Refresh 토큰은 `used_at`을 채워 더는 쓸 수 없게 한다 (행은 정리 때까지 남는다, 10-6).
- 실패 `401` `INVALID_REFRESH_TOKEN`. 쿠키가 없거나 비어 있음, DB에 없음(위조·이미 정리됨), 만료, 이미 사용됨을 구분하지 않는다. 실패 응답에서 쿠키를 지우지 않는다 (프론트가 로그인 화면으로 보내면 로그인 성공 때 덮어써진다).
- **재사용 감지**: 만료 전인데 `used_at`이 찬 토큰이 들어오면 탈취로 간주한다. 해당 관리자의 Refresh 토큰을 모두 삭제하고(다른 기기 포함) `WARN` 보안 이벤트 로그를 남긴 뒤 같은 `401`로 응답한다 (10-6).
- **동시 요청**: 같은 Refresh 토큰으로 요청 둘이 동시에 오면 하나만 성공하고 나머지는 재사용으로 판정되어 그 관리자의 토큰이 모두 지워진다 (성공한 쪽이 받은 새 토큰도 포함). 프론트가 한 번만 호출하도록 막는 것이 전제다 (10-3). 같은 브라우저의 다른 탭에서 동시에 호출하는 드문 경우에는 다시 로그인하게 되는 것을 받아들인다.

#### POST `/api/v1/auth/logout`
- 요청 본문 없음 (헤더 `X-XSRF-TOKEN` 필요). 인증 없이 호출할 수 있다: Access 토큰이 만료된 상태에서도 로그아웃되어야 한다 (10-5).
- `204`와 함께 두 쿠키를 `Max-Age=0`으로 내려 지운다 (이름·`Path`·`Secure`는 발급 때와 같게, 10-1).
- `refresh_token` 쿠키의 값(여러 개면 재발급과 같이 첫 값, 10-6)에 해당하는 행 하나만 삭제한다 (다른 기기의 로그인은 유지). 쿠키가 없거나 비어 있거나 DB에 없는 값이어도(이미 로그아웃, 만료 후 정리) 오류 없이 같은 `204`로 응답한다. 로그아웃은 여러 번 불러도 결과가 같고, 어떤 상태에서도 브라우저의 쿠키가 지워져야 하기 때문이다.

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

