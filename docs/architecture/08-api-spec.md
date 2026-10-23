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
- `csrf.spa()`는 모든 요청에서 토큰을 미리 불러오므로 다른 API 응답에도 같은 쿠키가 붙는다 (10-5). 그래도 이 API를 두는 이유는 프론트가 쿠키가 없을 때 부르는 명시적 진입점이 필요해서다. 첫 상태 변경 요청(로그인 POST) 전에 다른 API를 우연히 먼저 불렀는지에 기대지 않는다.

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
요청 (`PostRequest` record, `@Valid @RequestBody`. `PUT`도 같은 record를 쓴다)
```json
{
  "title": "Spring Security + JWT 정리",
  "slug": "spring-security-jwt",
  "summary": "HttpOnly 쿠키로 JWT 인증 구현하기",
  "content": "# 들어가며\n...",
  "thumbnailUrl": null,
  "categoryId": 1,
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
- 응답 DTO는 `PostCreateResponse(id, slug)`.
- 검증 실패: `400` (`INVALID_INPUT`), 없는 카테고리: `404` (`CATEGORY_NOT_FOUND`), slug 중복: `409` (`DUPLICATE_SLUG`)
- **`tags`는 M4에서 요청에 추가한다.** 태그(`Tag`, `PostTag`)가 M4에 구현되므로 M2에는 `tags` 필드가 없다. 보내도 알 수 없는 필드라 무시하고(아래 '본문 형식') 저장하지 않으며, 응답의 `tags`는 계속 `[]`다 (8-1). 거절하지 않는 이유: 받아서 버리는 필드를 record에 두면 검증 규칙(최대 10개, 각 1~50자)만 있고 동작이 없는 코드가 되고, 알 수 없는 필드는 무시한다는 규칙(카테고리·로그인과 같다)을 그대로 따르면 M4에서 필드와 검증을 더하는 것으로 끝난다. 태그의 정규화(6장)와 `PUT`에서 `tags`를 생략했을 때의 처리(전체 교체라 빈 목록이 된다)는 M4 설계에서 다룬다.

**검증 규칙** (검증 실패는 `400` `INVALID_INPUT`, `errors`에 필드별 사유)
| 필드 | 규칙 | 어기면 |
|---|---|---|
| title | 필수(`null`·빈 값·공백만 있는 값 거절, `@NotBlank`), 앞뒤 공백을 제거한 값 기준 1~200자, 제어 문자 금지(`@Pattern(regexp = "^\\P{Cc}+$")`) | `400` |
| slug | 필수(`@NotBlank`), 6장 slug 규칙(`@Pattern`), 최대 200자(`@Size(max = 200)`) | `400` |
| summary | 선택, 앞뒤 공백을 제거한 값 기준 최대 500자, 빈 값·공백만 있는 값은 `null`로 저장, NUL(`\u0000`) 금지 | `400` |
| content | 필수(`@NotBlank`), 최대 100,000자(`@Size(max = 100000)`), NUL 금지. 앞뒤 공백은 그대로 둔다 | `400` |
| thumbnailUrl | 선택, 앞뒤 공백을 제거한 값 기준 최대 500자, 빈 값·공백만 있는 값은 `null`로 저장, 값이 있으면 `http://` 또는 `https://`로 시작하는 URL이고 공백·제어 문자가 없어야 한다(`@Pattern(regexp = "^https?://[^\\s\\p{Cc}]+$")`) | `400` |
| categoryId | 선택. `null`이거나 없으면 카테고리 없는 글 | 없는 id: `404` |
| status | 필수, `DRAFT` 또는 `PUBLISHED` | `400` |

- **앞뒤 공백 제거와 빈 값 처리는 `PostRequest` 생성자에서 한다.** `title`·`summary`·`thumbnailUrl`을 `String.strip()`하고, `summary`·`thumbnailUrl`은 제거한 뒤 빈 값이면 `null`로 바꾼다 (검증·저장·응답이 모두 같은 값을 쓰게 하고, 폼이 빈 입력을 `""`로 보내도 DB에 빈 문자열이 남지 않게 한다). `strip()`의 공백 기준과 NBSP 처리는 카테고리 `name`과 같다 (8-4 카테고리).
- **NUL을 막는 이유**: PostgreSQL이 NUL이 든 문자열을 거절해 `DataIntegrityViolationException`이 되고, 이 예외는 slug 중복 판정과 겹친다(아래). 텍스트 필드(`title`·`summary`·`content`·`thumbnailUrl`)는 검증에서 NUL을 막는다. `title`·`thumbnailUrl`은 줄바꿈·탭 같은 제어 문자도 막고, `summary`·`content`는 줄바꿈·탭을 허용한다 (여러 줄 입력이 가능하다). `slug`는 패턴이 이미 막는다.
- **`content` 최대 100,000자**: 개인 블로그 글 길이에 충분하고, 어떤 입력이든 요청 본문이 Nginx `client_max_body_size`(기본값 1MB, 12-4) 안에 들게 하는 값이다. 한글은 UTF-8로 3바이트라 content만 최대 약 300KB이고, 줄바꿈·따옴표는 JSON에서 2바이트, 그 밖의 제어 문자는 6바이트(`\u00XX`)로 늘어도 최대 약 600KB다. 다른 필드를 더해도 1MB를 넘지 않는다. 그래서 앱이 먼저 `400`으로 거절하고 Nginx의 `413`은 직접 요청이 아니면 나오지 않는다. 길이는 Java 문자(UTF-16) 수로 센다 (컬럼 길이는 코드 포인트 수라 검증을 통과한 값이 컬럼을 넘지 않는다). 최대 길이의 `content`가 검증을 통과하는 것을 Controller 테스트로 확인한다.
- **`thumbnailUrl` 형식**: 이미지 업로드는 M6이라 외부 이미지 URL을 그대로 받는다. 스킴을 `http(s)`로 제한해 `javascript:`·`data:` 같은 값을 서버에서 거절한다. 호스트 존재 여부나 이미지 여부는 확인하지 않는다 (서버가 외부로 요청을 보내지 않는다).
- **`categoryId`**: 값이 있으면 `CategoryRepository.findById`로 조회하고 없으면(`0`·음수 포함) `404` `CATEGORY_NOT_FOUND`다 (경로 `id`와 같은 방침: 값 범위는 검사하지 않고 없는 id로 본다).
- **본문 형식** (Jackson 3.1.5 기본 변환을 확인한 결과이고, 카테고리 요청과 같은 방침이라 직접 파싱하지 않는다. Controller 테스트로 고정해 프레임워크 업그레이드로 바뀌면 드러나게 한다)
  - 본문이 없거나 JSON 파싱에 실패하면 `400` `INVALID_INPUT` (`errors` `[]`), JSON이 아닌 `Content-Type`은 `415`, 같은 키가 여러 개면 마지막 값, 알 수 없는 필드는 무시한다 (카테고리 8-4와 같다). 필드가 `null`이거나 없으면 그 필드의 필수 규칙을 따른다 (`title`·`slug`·`content`·`status`는 `400`, `summary`·`thumbnailUrl`·`categoryId`는 값 없음).
  - 문자열 필드(`title`, `slug`, `summary`, `content`, `thumbnailUrl`)의 숫자·불리언은 문자열로 변환된 뒤 위 규칙을 받고, 배열·객체는 `400` (`errors` `[]`) — 카테고리와 같다.
  - `status`: `"DRAFT"`·`"PUBLISHED"`만 받는다. `"draft"`(대소문자 다름), `""`, 없는 값(`"ARCHIVED"`), 불리언, 배열·객체는 `400` (`errors` `[]`, 열거형 변환 실패는 필드 오류가 아니라 파싱 실패다). 숫자 `0`·`1`은 Jackson 기본 동작으로 열거형 순서(`DRAFT`·`PUBLISHED`)로 받아들여진다: 프론트가 보내지 않는 형태라 막지 않고, 막으려면 Jackson 설정을 따로 바꿔야 하는 비용에 비해 얻는 것이 없다. `null`·없음은 필수 위반 `400`이다 (`errors`에 `status`).
  - `categoryId`: 정수는 그 값으로 조회한다. 숫자 문자열(`"1"`)은 `1`, 소수(`1.5`, `1.0`)는 소수점 아래를 버린 `1`, 빈 문자열(`""`)은 `null`(카테고리 없음)로 변환된다 (Jackson 기본 동작). 숫자로 읽히지 않는 문자열(`"abc"`), 불리언, 배열·객체, Long 범위를 넘는 수는 `400` (`errors` `[]`).
  - 한 필드가 여러 규칙을 어기면(`"slug": ""`는 필수와 패턴) `errors`에 같은 `field`로 항목이 여러 개 나올 수 있고 순서는 정하지 않는다.

#### GET `/api/v1/admin/posts`
쿼리 파라미터 (모두 선택): `page`, `size`(7장), `status`(`DRAFT` 또는 `PUBLISHED`)

```
GET /api/v1/admin/posts?page=0&size=10&status=DRAFT
```
`200 OK` — 응답은 `PageResponse<AdminPostSummaryResponse>` (공개 목록과 같은 페이지 형식, 8-1)
```json
{
  "content": [
    {
      "id": 13,
      "title": "작성 중인 글",
      "slug": "draft-post",
      "status": "DRAFT",
      "category": { "name": "Backend", "slug": "backend" },
      "publishedAt": null,
      "createdAt": "2026-10-12T09:00:00Z",
      "updatedAt": "2026-10-12T09:30:00Z"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "hasNext": false
}
```
- 요청은 `AdminPostSearchRequest(page, size, status)` record(`@Valid @ModelAttribute`, 5장)로 받는다. `page`·`size` 검증은 공개 목록(`PostSearchRequest`)과 같고, `status`만 다르다. 공개 목록의 요청 record에 `status`를 넣지 않는다: 공개 API가 DRAFT 조회 파라미터를 받는 길을 만들지 않기 위해서다.
- 공개 목록과 다른 응답 필드: `status`, `createdAt`, `updatedAt`이 있고 `publishedAt`은 한 번도 발행하지 않은 DRAFT면 `null`이다 (발행했다가 DRAFT로 돌린 글은 최초 발행 시각이 남는다, 글 작성·수정). 관리자 목록 화면이 쓰지 않는 `summary`, `thumbnailUrl`, `tags`는 넣지 않는다. 카테고리가 없는 글은 `"category": null` (공개와 같다).
- **`status` 값**: 필터를 주지 않으면(파라미터 없음, `?status=`) DRAFT·PUBLISHED 모두 조회한다. 값은 Spring 바인딩 기본 동작을 따른다 (7장 페이지네이션과 같은 방침: 직접 파싱하지 않는다).
  | 입력 | 결과 |
  |---|---|
  | `DRAFT`, `PUBLISHED` | 그 상태만 |
  | 앞뒤 공백이 있는 값(`%20DRAFT`, `DRAFT%20`) | 공백을 지운 값 기준으로 그 상태만 |
  | 없음, `?status=`, 공백만 있는 값(`%20`) | 전체 |
  | `draft`(대소문자 다름), `ARCHIVED`(없는 값), `null` | `400` `INVALID_INPUT` (`errors`의 `field`는 `status`, `reason`은 "형식이 올바르지 않습니다") |
  - 같은 키가 여러 개면 첫 값만 바꾸고 검사한다 (`page`와 같다): `?status=DRAFT&status=abc`는 `DRAFT`, `?status=abc&status=DRAFT`는 `400`, `?status=&status=DRAFT`는 전체. 첫 값이 공백만 있고 뒤에 값이 있는 `?status=%20&status=DRAFT`는 `400`이다 (빈 값과 달리 공백만 있는 값은 같은 키가 둘 이상이면 값 없음으로 처리되지 않는다).
  - 위 표와 같은 키 동작은 Spring Boot 4.1.1에서 `@WebMvcTest`로 확인한 결과다 (인코딩된 쿼리 문자열을 `URI`로 그대로 보내 디코딩까지 포함). Controller 테스트로 고정해 프레임워크 업그레이드로 바뀌면 드러나게 한다 (7장).
- **정렬: `id` 내림차순** (최근에 만든 글이 먼저). `publishedAt`은 DRAFT가 `null`이라 쓰지 않는다. `id`는 유일해 같은 값이 없으므로 페이지 사이에서 중복·누락이 없고, 관리자 목록은 발행 순서가 아니라 작성한 순서로 찾기 때문이다. `createdAt`을 쓰지 않는 이유: `id`가 작성 순서와 같고, 보조 정렬 키가 필요 없다. `(status, published_at)` 인덱스는 이 쿼리가 쓰지 않는다. 글이 수백 건인 개인 블로그라 순차 스캔으로 충분해 인덱스를 더하지 않는다.
- 범위 밖 페이지(`page`가 `totalPages` 이상)는 오류가 아니라 `200`에 `content: []`다.
- **조회 방식**: QueryDSL 없이 Spring Data 파생 쿼리 두 개(`findAllByOrderByIdDesc`, `findAllByStatusOrderByIdDesc`)로 하고, `status`가 없으면 앞의 것을 부른다 (서비스가 분기). 필터가 `status` 하나라 동적 쿼리가 필요 없다. 검색 조건이 늘어나는 M4에서 `PostQueryRepository`(QueryDSL, 5장)로 옮긴다. 카테고리는 `@EntityGraph(attributePaths = "category")`로 함께 읽어 글마다 쿼리가 나가지 않게 한다 (공개 목록과 같다).
- 정렬은 Repository 메서드 이름에 고정하고 Repository 테스트로 검증한다 (5장). 조회 로그는 남기지 않는다 (5장).

#### GET `/api/v1/admin/posts/{id}`
수정 폼을 채우는 용도다. 응답은 `AdminPostDetailResponse`로, 공개 상세(`PostDetailResponse`, 8-1)의 필드에 `status`, `categoryId`, `createdAt`을 더한다. PUBLISHED·DRAFT 모두 조회한다.

`200 OK`
```json
{
  "id": 12,
  "title": "Spring Security + JWT 정리",
  "slug": "spring-security-jwt",
  "summary": "HttpOnly 쿠키로 JWT 인증 구현하기",
  "content": "# 들어가며\n본문 Markdown...",
  "thumbnailUrl": null,
  "status": "PUBLISHED",
  "categoryId": 1,
  "category": { "name": "Backend", "slug": "backend" },
  "tags": [],
  "publishedAt": "2026-10-11T01:00:00Z",
  "createdAt": "2026-10-10T12:00:00Z",
  "updatedAt": "2026-10-11T03:30:00Z"
}
```
- `categoryId`는 폼의 카테고리 선택값이다 (작성 요청이 `categoryId`로 받는다). 카테고리가 없는 글은 `categoryId`와 `category`가 모두 `null`이다. `category`는 공개 상세와 같은 이름·slug 형태로, 폼이 선택된 항목을 표시하는 데 쓴다.
- `publishedAt`은 한 번도 발행하지 않은 DRAFT면 `null`이다 (발행했다가 DRAFT로 돌린 글은 최초 발행 시각이 남는다, `PUT`). `tags`는 M4 전까지 항상 `[]`다 (8-1). 조회수(`viewCount`)는 M6에서 구현하므로 넣지 않는다.
- 조회는 `PostRepository.findWithCategoryById(Long id)`(`@EntityGraph(attributePaths = "category")`)로 한다. 상태 조건이 없어 DRAFT·PUBLISHED 모두 조회되고(공개 상세의 `findBySlugAndStatus`와 다르다), 카테고리를 함께 읽어 `category`·`categoryId` 때문에 쿼리가 더 나가지 않는다. Repository 테스트로 검증한다.
- 없는 `id`: `404` `POST_NOT_FOUND`.
- 경로의 `id`가 Long으로 읽히지 않으면(`abc`, 범위 초과) `400` `INVALID_INPUT` (`errors` `[]`). Long으로 읽히면 그 값으로 조회하고, 없으면(`0`, 음수 포함) `404`다 (카테고리와 같다, 아래 '공통').
- `PUT`의 응답(`200` + 수정된 상세 응답)도 같은 `AdminPostDetailResponse`다.

**공통 (글 목록·단건 조회)**
- 검사 순서: 인증(`401`·`403`) → 쿼리·경로 형식과 검증(`400`) → 존재(`404`). 조회 API라 CSRF 헤더는 필요 없다 (GET).

#### PUT `/api/v1/admin/posts/{id}`
- 본문은 작성과 같은 `PostRequest` (전체 교체, 7장). `200` + 수정된 `AdminPostDetailResponse` (단건 조회 응답과 같은 DTO).
- 보낸 값으로 `title`, `slug`, `summary`, `content`, `thumbnailUrl`, `category`, `status`를 모두 바꾼다. `summary`·`thumbnailUrl`·`categoryId`를 생략하거나 `null`로 보내면 그 값이 지워진다(카테고리 없는 글). 응답의 `tags`는 M4 전까지 `[]`다.
- **slug 변경을 허용한다.** 자기 자신과 같은 slug로 보내는 것은 중복이 아니다 (다른 값만 바꾸는 경우). 다른 글이 쓰는 slug면 `409` (`DUPLICATE_SLUG`). PUBLISHED 글의 slug를 바꾸면 기존 `/posts/{slug}` 주소는 곧바로 `404`가 된다 (리다이렉트·이전 slug 보관 없음, 카테고리 slug와 같다. 개인 블로그라 외부 링크가 거의 없다). 바꿀 때의 경고는 관리자 화면 이슈에서 다룬다.
- 같은 내용으로 `PUT`하면 변경 감지에 걸리지 않아 `updatedAt`도 그대로다.
- 없는 `id`: `404` (`POST_NOT_FOUND`). 본문의 `categoryId`가 없는 카테고리면 `404` (`CATEGORY_NOT_FOUND`).

#### DELETE `/api/v1/admin/posts/{id}`
- `204`. 없는 `id`: `404` (`POST_NOT_FOUND`). 같은 `id`를 두 번 지우면 두 번째는 `404`다 (카테고리 삭제와 같다). 하드 삭제다 (6장). 연결된 `post_tag` 행은 DB FK `ON DELETE CASCADE`가 지운다.
- **삭제 쿼리는 `@Modifying` JPQL 한 문장(`delete from Post p where p.id = :id`)이 지운 행 수를 돌려준다.** 0이면 `404`다. `findById` 후 `delete`로 하지 않는 이유는 카테고리 삭제와 같다 (조회와 삭제 사이에 다른 요청이 먼저 지우면 삭제가 0행이라 Hibernate가 예외를 던져 `500`이 된다). `post_tag` 정리를 DB에 맡기므로 M4에서 `PostTag` 엔티티를 매핑해도 이 삭제는 `PostTag`를 읽지 않는다 (`PostTag`에 `cascade`를 걸지 않는다).

**발행 시각 (`publishedAt`, 6장)**: 서비스가 `Clock`으로 정한 시각을 쓴다 (5장).
| 요청 | 글의 이전 상태 | 결과 |
|---|---|---|
| 작성 `DRAFT` | - | `publishedAt = null` |
| 작성 `PUBLISHED` | - | `publishedAt = now` |
| 수정 `PUBLISHED` | 한 번도 발행하지 않은 `DRAFT` (`publishedAt = null`) | `publishedAt = now` (최초 발행) |
| 수정 `PUBLISHED` | `PUBLISHED`, 또는 발행했다가 `DRAFT`로 돌린 글 | 기존 `publishedAt` 유지 |
| 수정 `DRAFT` | `PUBLISHED` | `status`만 `DRAFT`, `publishedAt`은 **지우지 않고 유지** |
- 발행했던 글을 `DRAFT`로 돌려도 `publishedAt`을 지우지 않는다: 6장이 `publishedAt`을 "최초 발행 시각"이고 이후 바뀌지 않는다고 정했고, 다시 발행할 때 목록 순서(`publishedAt` 내림차순)가 바뀌지 않게 하기 위해서다. 공개 API는 PUBLISHED만 조회하므로(6장) DRAFT의 `publishedAt`은 공개되지 않는다. 관리자 응답에서 DRAFT의 `publishedAt`은 "한 번도 발행하지 않았으면 `null`, 발행했다가 돌린 글이면 최초 발행 시각"이다.
- 엔티티는 기존 `publish(now)`(상태를 PUBLISHED로 바꾸고 `publishedAt`이 비었을 때만 `now`를 넣는다)를 그대로 쓰고, DRAFT로 돌리는 메서드를 더한다. 서비스는 요청의 `status`에 따라 둘 중 하나를 부른다.

**공통 (글 작성·수정·삭제)**
- 경로의 `id`가 Long으로 읽히지 않으면(`abc`, 범위 초과) `400` `INVALID_INPUT` (`errors` `[]`). Long으로 읽히면 그 값으로 조회하고, 없으면(`0`, 음수 포함) `404`다 (단건 조회와 같다).
- 검사 순서: 인증·CSRF(`401`·`403`) → 경로·본문 형식과 검증(`400`) → 글 존재(`404` `POST_NOT_FOUND`, 수정·삭제) → 카테고리 존재(`404` `CATEGORY_NOT_FOUND`, 작성·수정) → slug 중복(`409`). 그래서 없는 글에 잘못된 본문을 보내면 `400`, 없는 글에 없는 카테고리를 보내면 `POST_NOT_FOUND`, 없는 카테고리에 중복 slug를 보내면 `CATEGORY_NOT_FOUND`다.
- **slug 중복 판정은 DB 유니크 제약(`post.slug`)에 맡긴다.** 카테고리와 같은 이유로 확인 쿼리 없이 저장 시점의 제약 위반을 `DUPLICATE_SLUG`로 바꾼다. 제약 위반이 서비스 밖에서 나오면 `500`이 되므로 `saveAndFlush`(작성)와 수정 뒤 `flush`(수정)를 서비스 안에서 호출한다.
  - **카테고리와 다른 점: 제약 이름으로 가린다.** `post`에는 유니크 말고도 위반할 수 있는 제약이 있다 (`category_id` FK: `categoryId` 조회와 저장 사이에 그 카테고리가 지워지는 경쟁). `DataIntegrityViolationException`의 원인 `org.hibernate.exception.ConstraintViolationException`의 제약 이름이 slug 유니크(`post_slug_key`)일 때만 `DUPLICATE_SLUG`로 바꾸고, 그 밖의 위반은 그대로 던져 `500`으로 둔다 (기대하지 않은 오류를 `409`로 잘못 알리지 않는다). 제약 이름이 실제 PostgreSQL 오류에서 꺼내지는지는 통합 테스트(실제 DB)로 확인한다 (Service 테스트는 예외를 mock으로 만들어 이를 확인할 수 없다).
  - 수정의 flush에서 `ObjectOptimisticLockingFailureException`(UPDATE가 0행: `findWithCategoryById` 뒤 flush 전에 다른 요청이 그 글을 지움)이 나오면 `404` (`POST_NOT_FOUND`)로 바꾼다.
  - 수정은 글을 `PostRepository.findWithCategoryById`(단건 조회와 같은 메서드)로 읽는다. 응답이 `category`를 쓰므로 카테고리가 바뀌지 않는 경우에도 추가 쿼리가 나가지 않는다.
- **관리자 행위 로그** (5장): 성공한 뒤에만 `PostService`가 `INFO`로 남긴다. 작성·수정은 `id`와 `slug`, 삭제는 `id`만 넣는다. `title`은 사용자 입력이라 줄바꿈 등으로 로그 줄을 위조할 수 있어 넣지 않는다 (카테고리 `name`과 같다). slug는 패턴 검증을 통과한 값이다. 실패(`404`·`409`)는 `GlobalExceptionHandler`의 `WARN`이 기록이다.
- 공개 API(`/api/v1/posts`)는 변경 즉시 바뀐 DB 값을 돌려준다. 공개 페이지 반영 지연(프론트 데이터 캐시의 stale-while-revalidate, 상한 없음)과 관리자 화면의 안내는 11장 '렌더링 전략'·'관리자 글 목록'.

#### POST `/api/v1/admin/categories`
요청 (`CategoryRequest` record, `@Valid @RequestBody`)
```json
{ "name": "Backend", "slug": "backend" }
```
`201 Created`
```
Location: /api/v1/admin/categories/1
```
```json
{ "id": 1, "name": "Backend", "slug": "backend" }
```
- 응답 DTO는 생성·수정 공통 `AdminCategoryResponse(id, name, slug)`. 글 개수를 세는 `CategoryResponse`(8-1)와 달리 `postCount`가 없다: 방금 만든·고친 카테고리라 프론트가 목록을 다시 불러 갱신한다 (관리자용 목록 API를 따로 두지 않는다, 8-1).
- `Location`은 단건 조회가 없는 리소스(`GET /admin/categories/{id}`는 두지 않는다)의 식별용이다. 7장 `201` 규칙을 따른다.
- 이름은 중복을 허용한다 (`name`에 유니크 제약이 없다, 6장). 식별은 `slug`로 하고 `name`은 표시용이다.
- slug 중복: `409` (`DUPLICATE_SLUG`).

**검증 규칙** (글 작성의 규칙 표와 같은 방식, 검증 실패는 `400` `INVALID_INPUT`, `errors`에 필드별 사유)
| 필드 | 규칙 | 어기면 |
|---|---|---|
| name | 필수(`null`·빈 값·공백만 있는 값 거절, `@NotBlank`), 앞뒤 공백을 제거한 값 기준 1~50자, 제어 문자 금지(`@Pattern(regexp = "^\\P{Cc}+$")`, 유니코드 `Cc`: U+0000~001F, U+007F~009F) | `400` |
| slug | 필수(`null`·빈 값·공백만 있는 값 거절, `@NotBlank`), 6장 slug 규칙(`@Pattern`), 최대 100자(`@Size(max = 100)`, `category.slug` 컬럼) | `400` |

- name의 앞뒤 공백은 `CategoryRequest` 생성자에서 `String.strip()`으로 제거해, 검증·저장·응답이 모두 제거된 값을 쓴다 (`" Backend "`는 `"Backend"`). `strip()`과 `@NotBlank`는 둘 다 `Character.isWhitespace` 기준이라(5장 관리자 username과 같다) 전각 공백(U+3000)·탭·줄바꿈은 제거되고, 그것만 있는 값은 빈 값이 되어 `400`이다. NBSP(U+00A0)는 `isWhitespace`가 공백으로 보지 않아 제거도 거절도 하지 않는다 (받아들인다: 관리자 1명의 입력이고 표시만 어색할 뿐이다). 안쪽 공백은 그대로 둔다.
- 제어 문자(NUL 포함)가 안쪽에 있는 name은 `400`이다. PostgreSQL이 NUL을 거절해 `DataIntegrityViolationException`이 되면 `409 DUPLICATE_SLUG`로 잘못 나가므로 검증에서 막는다. 제거 대상인 앞뒤의 줄바꿈·탭은 위 `strip()`이 먼저 지운다. 길이는 Java 문자(UTF-16) 수로 세어, 컬럼 길이(코드 포인트 수)를 넘는 값이 검증을 통과하지 않는다.
- slug는 공백을 제거하지 않는다. 앞뒤 공백·대문자·밑줄·연속 하이픈은 패턴 위반으로 `400`이다 (글 slug와 같다).
- 한 필드가 여러 규칙을 어기면(`"slug": ""`는 필수와 패턴) `errors`에 같은 `field`로 항목이 여러 개 나올 수 있다. 순서는 정하지 않는다.
- 본문이 없거나 JSON 파싱에 실패하면 `400` `INVALID_INPUT` (`errors` `[]`), JSON이 아닌 `Content-Type`은 Spring 기본 `415`, 같은 키가 여러 개면 마지막 값, 알 수 없는 필드는 무시한다 (모두 로그인 요청과 같다, 8-3). 필드가 `null`이거나 없으면 필수 위반 `400`이다.
  - 필드 값이 배열·객체면 `400` `INVALID_INPUT` (`errors` `[]`), 숫자·불리언은 Jackson 기본 변환으로 문자열이 된 뒤 위 규칙을 받는다 (`"slug": 1`은 `"1"`이라 통과, `"name": true`는 `"true"`). 같은 키·변환·배열·객체 동작은 Controller 테스트로 고정한다 (프레임워크 업그레이드로 바뀌면 드러나게, 8-3).

#### PUT `/api/v1/admin/categories/{id}`
- 본문은 생성과 같다 (전체 교체). `200` + 수정된 `AdminCategoryResponse`.
- 자기 자신과 같은 slug로 보내는 것은 중복이 아니다 (이름만 바꾸는 경우). 다른 카테고리가 쓰는 slug면 `409` (`DUPLICATE_SLUG`).
- slug를 바꾸면 기존 slug는 곧바로 쓸 수 없다 (리다이렉트·이전 slug 보관 없음). 글은 `category_id`로 연결되어 있어 글의 연결은 유지된다. `/categories/{slug}` 같은 기존 링크는 `404`가 되는 것을 받아들인다 (개인 블로그, 외부 링크가 거의 없다).
- 없는 `id`: `404` (`CATEGORY_NOT_FOUND`).

#### DELETE `/api/v1/admin/categories/{id}`
- `204`. 없는 `id`: `404` (`CATEGORY_NOT_FOUND`). 같은 `id`를 두 번 지우면 두 번째는 `404`다 (글 삭제와 같다).
- 그 카테고리의 글(DRAFT 포함)은 지워지지 않고 `category_id`만 `NULL`이 된다 (6장 FK `ON DELETE SET NULL`). 공개 API는 `"category": null`로 응답한다 (8-1).

**공통 (카테고리 생성·수정·삭제)**
- 경로의 `id`가 Long으로 읽히지 않으면(`abc`, 범위 초과) `400` `INVALID_INPUT` (`errors` `[]`). Long으로 읽히면 그 값으로 조회하고, 없으면(`0`, 음수 포함) `404`다.
- 검사 순서: 인증·CSRF(`401`·`403`) → 경로·본문 형식과 검증(`400`) → 존재(`404`) → slug 중복(`409`). 그래서 없는 `id`에 잘못된 본문을 보내면 `400`이고, 없는 `id`에 중복 slug를 보내면 `404`다.
- **slug 중복 판정은 DB 유니크 제약(`category.slug`)에 맡긴다.** 서비스가 먼저 `exists`로 확인하면 확인과 저장 사이에 같은 slug가 들어올 수 있어 어차피 제약 위반을 따로 처리해야 하므로, 확인 쿼리 없이 저장 시점의 제약 위반만 `DUPLICATE_SLUG`로 바꾼다. `category`에는 유니크 외에 위반할 수 있는 제약이 없다고 본다: NOT NULL·길이는 앞선 검증이 막고, PostgreSQL이 거절하는 NUL은 name 검증(제어 문자 금지)이 막는다.
  - 제약 위반은 INSERT·UPDATE 시점에 나와야 서비스 안에서 잡을 수 있으므로 `saveAndFlush`(생성)와 수정 뒤 `flush`(수정)를 서비스 안에서 호출한다. 수정을 변경 감지에만 맡기면 위반이 트랜잭션 커밋 때 서비스 밖에서 나와 `500`이 된다.
  - 수정의 flush에서 `ObjectOptimisticLockingFailureException`(UPDATE가 0행: `findById` 뒤 flush 전에 다른 요청이 그 카테고리를 지움)이 나오면 `404` (`CATEGORY_NOT_FOUND`)로 바꾼다. 삭제를 행 수로 판정하는 것과 같은 원칙이다(아래).
  - 잡는 예외는 `DataIntegrityViolationException`이다. 관리자가 1명이라 실제 경쟁은 이중 클릭 정도지만, 같은 경로로 순차 중복도 처리되므로 분기가 하나다.
- **삭제는 DB FK에 맡기고, 삭제 쿼리는 `@Modifying` JPQL 한 문장(`delete from Category c where c.id = :id`)이 지운 행 수를 돌려준다.** 0이면 `404`다.
  - JPA가 글의 `category`를 NULL로 바꾸려면 글을 모두 읽어 와야 하고(글이 많으면 N건 UPDATE), 읽어 온 `Post`와 DB 값이 어긋날 수 있다. FK `ON DELETE SET NULL`은 DB가 같은 문장 안에서 처리한다.
  - 벌크 삭제는 영속성 컨텍스트를 거치지 않으므로, 같은 트랜잭션에서 이미 읽은 `Post`는 옛 `category`를 들고 있다. 삭제 서비스 메서드는 글을 읽지 않고 하나의 문장만 실행하므로 어긋날 곳이 없다. 이 메서드에서 글을 읽는 코드를 추가하지 않는다.
  - `findById` 후 `delete`로 하지 않는 이유: 조회와 삭제 사이에 다른 요청이 먼저 지우면 삭제가 0행이라 Hibernate가 예외를 던지고 `500`이 된다. 지운 행 수로 판정하면 이 경우도 `404`다.
- **관리자 행위 로그** (5장): 성공한 뒤에만 서비스가 `INFO`로 남긴다. 생성·수정은 `id`와 `slug`, 삭제는 `id`만 넣는다. `name`은 사용자 입력이라 줄바꿈으로 로그 줄을 위조할 수 있어 넣지 않는다(5장 로그인 실패 로그와 같은 이유). slug는 패턴 검증을 통과한 값이다. 실패(`404`·`409`)는 `GlobalExceptionHandler`의 `WARN`이 기록이다.
- 공개 목록(`GET /api/v1/categories`)은 변경 즉시 바뀐 DB 값을 돌려준다. 프론트의 캐시 무효화는 관리자 화면 이슈에서 다룬다.
