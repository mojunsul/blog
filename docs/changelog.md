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
  - access·refresh 쿠키 `Secure`는 `blog.auth.secure-cookie`(local만 `false`), 쿠키는 `@Component` `CookieUtil`에서 생성 (10-1)
  - JWT: HS256, `sub`·`iat`·`exp`, 키는 Base64 디코딩 32바이트 이상·기동 시 검사(local·test는 비면 무작위 키), `AuthProperties`, 필터는 잘못된 토큰을 무시하고 인가 단계의 `401`에 맡김, 아이디가 없어도 더미 해시로 BCrypt 비교, Refresh 토큰 생성·해시 방식 (10-5, 10-6, 12-2)
  - Security 경로 규칙에 `POST /api/v1/auth/login` 허용 추가, `JwtAuthenticationFilter`는 빈이 아니게 등록 (10-5)
  - JWT 키 판정은 `strip()` 후 표준 Base64 디코더, `access_token` 쿠키가 여러 개면 첫 값만 사용(Path를 `/api`보다 길게 바꾸지 않는 제약 포함) (10-6)

- **v2.16**
  - 설계 문서를 `docs/architecture/NN-*.md` 장별 파일로 분리하고 `docs/architecture.md`는 목차로 변경, 변경 이력을 `docs/changelog.md`로 분리 (장 본문은 변경 없음, 00장 머리말과 4장 구조 그림만 새 구조에 맞게 수정)

- **v2.17**
  - `refresh_token.used_at` 추가: 재발급해도 행을 지우지 않고 `used_at`을 채워 "쓴 토큰"과 "없는 토큰"을 구분, 재사용(만료 전 쓴 토큰) 감지 시 그 관리자의 토큰을 모두 삭제하고 `WARN` 로그 (6장, 8-3, 10-6, 5장)
  - 재발급은 조건부 UPDATE 한 문장으로 동시 요청을 하나만 성공시키고, 감지 삭제가 롤백되지 않게 `noRollbackFor = BusinessException.class`를 통합 테스트로 고정 (10-6)
  - 로그아웃은 인증 없이 허용(CSRF는 검사), 쿠키가 없거나 무효여도 `204`와 만료 쿠키, 쿠키 값에 해당하는 행만 삭제. 재발급·로그아웃 경로를 Security 규칙에 추가 (8-3, 10-1, 10-3, 10-5)
  - 만료 정리: 매일 04:00 KST `@Scheduled` (`RefreshTokenCleaner`, `SchedulingConfig`) (6장, 10-6)
  - 만료는 `expires_at <= now`로 한 번 정의해 재발급·재사용 판정·정리가 따름, `refresh_token` 쿠키가 여러 개면 첫 값(재발급·로그아웃 공통, Path 제약 포함), `RefreshTokenCleaner`는 `domain/auth/scheduler`, 5장 구조도에 `SchedulingConfig`·Cleaner·V2 마이그레이션 반영 (5장, 8-3, 10-6)

- **v2.18**
  - 관리자 카테고리 생성·수정·삭제 API 명세: 요청 검증(name 앞뒤 공백 제거 후 1~50자, slug 6장 패턴·100자), 이름 중복 허용, 생성·수정 응답 `AdminCategoryResponse(id, name, slug)`, 경로 `id` 형식 오류 `400`, 검사 순서(`400` → `404` → `409`) (6장, 8-4)
  - slug 중복은 확인 쿼리 없이 DB 유니크 제약 위반을 서비스 안에서 flush로 잡아 `DUPLICATE_SLUG`로 변환 (8-4)
  - 삭제는 `@Modifying` JPQL 벌크 삭제의 행 수로 `404`를 판정하고 글의 `category_id`는 FK `ON DELETE SET NULL`에 맡김, 영속성 컨텍스트 어긋남 방지 규칙 (8-4)
  - 카테고리 관리자 행위 로그는 `id`·`slug`만 남기고 `name`은 제외, 5장 구조도에 category 패키지 구성 반영 (5장, 8-4)
  - 설계 검증 반영: slug 필수 `@NotBlank`, name 제어 문자(NUL 포함) 금지 `400`, 앞뒤 공백은 `strip()`(전각 공백 제거, NBSP는 받아들임), 수정·삭제 경쟁의 `ObjectOptimisticLockingFailureException`은 `404`, 숫자·불리언·배열·객체 필드 값의 결과 명시 (8-4)

- **v2.19**
  - 관리자 글 목록·단건 조회 API 명세: `AdminPostSearchRequest(page, size, status)`, `status` 값(대소문자 구분·없는 값 `400`, 앞뒤 공백은 지움, 값 없음·공백만 있는 값은 전체, 같은 키는 첫 값), 정렬 `id` 내림차순, 범위 밖 페이지는 `200` 빈 목록, 응답 `AdminPostSummaryResponse`·`AdminPostDetailResponse`(`PUT` 응답도 후자), 단건 `id` 형식 오류 `400`·없으면 `404 POST_NOT_FOUND` (DRAFT도 조회), 검사 순서 (5장, 8-4)
  - 목록 조회는 QueryDSL 없이 파생 쿼리 두 개로 하고 M4에 `PostQueryRepository`로 이전 (8-4)
  - 단건 조회는 `findWithCategoryById`(`@EntityGraph`, 상태 조건 없음)로 하고 Repository 테스트로 검증 (8-4)

- **v2.20**
  - 관리자 글 작성·수정·삭제 API 명세: 작성·수정 공통 요청 `PostRequest`, 작성 응답 `PostCreateResponse(id, slug)`, 필드 검증(title 앞뒤 공백 제거 후 1~200자·제어 문자 금지, summary·thumbnailUrl 빈 값은 `null`, content 최대 100,000자, thumbnailUrl은 `http(s)` URL 최대 500자, 텍스트 필드 NUL 금지), `status`·`categoryId` 입력 변환 결과, 검사 순서(`400` → 글 `404` → 카테고리 `404` → `409`) (5장, 8-4)
  - `tags`는 M2 요청에 두지 않고 M4에서 추가(보내도 무시, 응답 `[]`) (8-4)
  - 발행 시각: PUBLISHED → DRAFT로 돌려도 `publishedAt`을 유지하고 다시 발행해도 최초 시각을 쓴다. 관리자 응답의 DRAFT `publishedAt`은 "발행 이력이 없으면 `null`"로 수정 (6장, 8-4)
  - 수정은 slug 변경을 허용(기존 주소는 `404`, 자기 slug는 중복 아님), slug 중복은 제약 이름(`post_slug_key`)으로 가려 FK 등 다른 제약 위반을 `409`로 오인하지 않음, 삭제는 `@Modifying` 벌크 삭제의 행 수로 `404` 판정 (8-4)
  - 글 행위 로그는 `id`·`slug`만 남기고 `title` 제외 (5장), Nginx `client_max_body_size`는 1MB 이상으로 명시 (12-4)

- **v2.21**
  - OpenAPI 명세를 프론트 타입 생성의 입력으로 정리: 응답 DTO의 nullable 필드에만 `@Schema(nullable = true)`, `OpenApiConfig`의 `OpenApiCustomizer`가 `Response` 스키마의 모든 property를 `required`로 하고 `$ref`+`null`을 `oneOf`로 변환(단위 테스트로 고정), 에러 응답·상태 코드·쿼리 파라미터 펼침은 명세에 반영하지 않음 (5장)
  - 프론트 `API 타입 생성`: `gen:api` 실행 조건, `openapi-typescript` 정확한 버전 고정, 생성 파일 ESLint 제외, 별칭 파일 없이 파일별 한 줄 별칭, `lib/api/types.ts` 삭제, `ProblemDetail`은 `errors.ts`에 직접 정의, 명세 불일치는 백엔드에서 수정, 재생성 누락은 CI 대신 PR 규칙으로 막음 (11장)

- **v2.22**
  - 관리자 화면 접근 제어를 `proxy.ts`에서 클라이언트 `AdminAuthGuard`(`GET /auth/me`)로 변경: `access_token`(`Path=/api`)이 `/admin` 페이지 요청에 실리지 않아 쿠키 검사가 불가능하고, `Path` 확대·표시 쿠키 추가는 채택하지 않음 (10-1, 11장)
  - 프론트 `관리자 인증`: `client.ts`의 경로·CSRF 헤더(쿠키 없으면 `GET /auth/csrf` 먼저)·401 재발급 한 번 재시도(`singleFlight`)·실패 시 `/admin/login?next=` 이동, `parseNextParam` 열린 리다이렉트 방지, `loginSchema`와 로그인 결과별 화면, 로그아웃 위치, 화면 확인 목록 (11장)
  - 로컬 `rewrites`는 `next dev`에서만, 목적지는 `API_INTERNAL_URL` (11장)
  - CSRF 토큰 호출 시점을 "화면 진입 시"에서 "상태 변경 요청 전 쿠키가 없을 때"로 변경 (8-3, 10-2, 10-3)
  - 설계 검증 반영: `next` 쿼리 같은 키 여러 개는 첫 값만 검사, `GET /auth/csrf` 실패(비2xx·쿠키 없음·네트워크)는 원래 요청을 보내지 않고 오류로 던짐, `rewrites`의 `API_INTERNAL_URL` 검사는 개발 분기 안에서만 하고 `.env.local` 없는 빌드를 확인 수단에 추가 (11장)

- **v2.23**
  - 프론트 `관리자 글 목록`(11장): 관리자 레이아웃 메뉴("글"·"카테고리")·로그아웃, `/admin` → `/admin/posts` redirect, URL `?status=&page=`(`page`는 공개 목록과 같은 규칙, `status`는 `parseStatusParam`으로 `DRAFT`·`PUBLISHED`만), 잘못된 값은 API 호출 없이 "잘못된 주소입니다.", 클라이언트 `AdminPostList`·`lib/api/admin-posts.ts`, 상태별 문구, `window.confirm` 삭제와 결과별 처리(`404`는 목록 갱신), 다시 불러오는 동안에도 "불러오는 중...", 공개 반영 지연 안내, 화면 확인(빈 목록은 local 시드 글을 API로 지운 뒤, 로딩·버튼 막기·"첫 페이지로" 포함)
  - 로그아웃 실패 문구, `Pagination`의 링크 주소를 `pageHref` prop으로 받음, '관리자 인증' 화면 확인의 `next` 값을 `/admin/posts` 기준으로 수정 (11장)
  - 공개 반영 지연을 stale-while-revalidate 동작(60초 뒤 첫 요청이 갱신 시작, `expire` 없음이라 상한 없음, 클라이언트 캐시 `stale` 5분)으로 정정 (11장 '렌더링 전략'), 8-4는 그 항목을 가리킨다
  - 로그아웃 상태의 `/admin`은 가드가 `redirect`보다 먼저라 `/admin/login?next=%2Fadmin`으로 간다고 정정 ('관리자 글 목록' 레이아웃·화면 확인 1번, '관리자 인증' 화면 확인 6번) (11장)

- **v2.24**
  - 프론트 `관리자 글 작성·수정`(11장): `PostForm`이 카테고리(`GET /categories`, 새 `lib/api/admin-categories.ts`)와 글을 불러와 폼을 채움, 수정 경로 `id`는 `parsePostIdParam`으로 숫자만 통과, 입력 항목(태그는 M4), `postSchema`(서버 규칙·메시지와 같음, slug는 `lib/slug.ts`의 `SLUG_PATTERN`·`MAX_SLUG_LENGTH` 공유), 저장 결과별 화면(`400` `errors`·`409`·`404`를 필드·폼 오류로), 발행된 글의 slug 변경 confirm, `beforeunload` 이탈 경고(앱 안 클라이언트 이동은 경고하지 않음), 화면 확인 목록
  - 미리보기(`MarkdownPreview`)는 브라우저에서 react-markdown 동기 `Markdown` + `remarkGfm`로 렌더링하고 코드 하이라이팅은 하지 않음, raw HTML 금지 유지 (11장, 10-4)
  - 8-4 `PUT`의 slug 변경 경고 위치를 11장 '관리자 글 작성·수정'으로 연결
  - 설계 검증 반영(11장): 불러오기는 `Promise.allSettled` 후 `401` > 글 `404` > 그 외 순으로 판정, 목록에 없는 `categoryId`는 초기값 `""`, 미리보기 중 본문 오류는 "작성"으로 전환하고 포커스, 화면 확인에 `postSchema` 길이·제어 문자·NUL 경계 추가, 화면 확인 2번에 본문 앞뒤 공백 유지·요약·썸네일 URL의 앞뒤 공백 제거 후 길이와 공백만 있는 값(`null` 저장)·썸네일 URL 제어 문자 확인 추가, 콘솔 입력 절차에 `bubbles: true` 명시, 제어 문자는 가운데에 넣고 썸네일 URL은 `\s`에 들지 않는 `\u0001`로 확인, `null` 저장은 수정 화면의 `GET` 응답으로 확인, 앞뒤 공백을 붙인 200자 제목·200자 slug 통과 확인 추가

- **v2.25**
  - 프론트 `관리자 카테고리`(11장): `/admin/categories` 한 화면에 생성·수정 공용 `CategoryForm`과 목록 표(이름·slug·발행 글 수), 저장·삭제 뒤 `getCategories()`로 목록을 다시 불러옴, `lib/api/admin-categories.ts`에 생성·수정·삭제 함수 추가
  - `categorySchema`를 `schemas/category.ts`로 두고 서버 `CategoryRequest`와 같은 규칙·메시지(slug 상한 100자), 저장·삭제 결과별 화면(`409` slug 아래, 수정 중 `404`는 고정 문구, 삭제 `404`는 성공과 같음)
  - 삭제 확인은 글 유무와 상관없이 항상 같은 문구(`postCount`는 PUBLISHED만 셈), 카테고리 slug 변경 확인은 M4로 미룸
  - 8-4 카테고리 공개 반영 지연 항목을 11장 '렌더링 전략'·'관리자 카테고리'로 연결
  - 설계 검증 반영: '프론트엔드 테스트'의 단위 테스트 제외 목록에 `categorySchema`와 '관리자 카테고리' 화면 확인 추가, 화면 확인에 저장·삭제 뒤 다시 불러오기 실패(7번)와 저장 `401` 로그인 이동(5번), 삭제 `401` 로그인 이동(6번) 추가

- **v2.26**
  - Docker 이미지(12-7): 멀티 스테이지, 공식 이미지 메이저 태그 고정(`eclipse-temurin:21-jdk`·`21-jre`, `node:24-alpine`), 베이스 이미지의 일반 사용자(`ubuntu`·`node`)로 실행, 비밀값은 이미지에 넣지 않음
  - backend: wrapper로 `bootJar`(테스트 제외), layered jar 추출 후 레이어별 `COPY`, 기본 프로필은 이미지의 `SPRING_PROFILES_ACTIVE=prod`, JVM 옵션은 compose `JAVA_TOOL_OPTIONS`
  - frontend: standalone·static·public만 복사, `HOSTNAME=0.0.0.0`, 빈 `public`을 위한 `.gitkeep`, `.nvmrc`와 태그를 함께 바꿈
  - 확인 방법(로컬 빌드·기동·실행 사용자) 추가, 4장·목차에 반영
  - 설계 검증 반영: backend 의존성 캐시를 `./gradlew dependencies` 선행(jar를 받지 않음)에서 BuildKit 캐시 마운트로 바꾸고 효과 범위(로컬 재빌드만, CI 제외)를 적음

- **v2.27**
  - 운영 compose(12-2): 프로젝트 이름 `blog-prod`(로컬 compose와 볼륨이 섞이지 않게), `image`(`ghcr.io/mojunsul/blog-*:${IMAGE_TAG:-latest}`)와 `build`를 함께 두어 로컬 빌드와 GHCR 이미지를 같은 파일로 씀, 호스트 포트는 nginx `80`만, postgres·backend healthcheck와 기동 순서, 로그 제한을 `x-logging` 앵커로 공통 적용, `.env.example`에 `IMAGE_TAG`
  - Nginx(12-4): `nginx:1-alpine`, 접근 로그에 `rid=$request_id`, gzip, `client_max_body_size 2m`, rate limit(로그인 `5r/m` burst 5, `/api/` `10r/s` burst 20, `limit_req_status 429`), frontend `proxy_buffering off`(스트리밍), 프록시·보안 헤더는 `server` 블록에(`X-Frame-Options: DENY`, `X-Powered-By` 숨김)
  - 429·502는 Nginx 기본 HTML이고 프론트는 기존 "그 외" 문구로 처리(변경 없음), 로컬 HTTP 로그인은 `Secure` 쿠키를 끄지 않고 Chromium 계열 브라우저로 확인
  - 확인 방법 추가, 10-4·8-4·12-3·12-6(`up -d --no-build`)·12-7에 값과 참조 반영
  - 설계 검증 반영: Nginx `resolver 127.0.0.11` + 변수 `proxy_pass`로 업스트림 이름을 요청 때 확인(기동 순서·컨테이너 재생성 후 502 방지, 3장 문구 일반화), 운영 compose의 `POSTGRES_PASSWORD`는 `:?` 필수·DB 이름·사용자 기본값은 postgres와 backend가 같게(`environment` 치환), 429 문구는 화면별 "그 외" 행 참조로, 확인 방법에 재생성 후 502·비밀번호 누락·작성 후 목록 순서 추가
  - 설계 검증 반영: 재생성 확인 명령에 `-f infra/docker-compose.prod.yml`과 `IMAGE_TAG=local`을 붙이고, `-p blog-prod`만 쓰는 범위를 조회 명령(`ps`·`logs`)으로 좁힘
- **v2.28**
  - 서버 준비(12-2): A1.Flex 4 OCPU·24GB Ubuntu 24.04, 예약 공인 IP, 보안 목록(22는 본인 IP)·iptables(Docker 설치 전), Docker 공식 apt 저장소, 저장소 clone, 첫 배포는 서버에서 빌드(`.env`의 `IMAGE_TAG=local`), 관리자 비밀번호 변경 명령
  - HTTPS(12-3): Cloudflare A 레코드 DNS only, 저장소에 도메인을 두지 않음(`server_name _`, `--cert-name blog`), `certbot/certbot:v5.8.0`(`profiles`, 상주 안 함), 인증서·웹루트는 `infra/certbot/` bind mount
  - 최초 발급은 임시 Nginx + webroot(`--dry-run` 먼저), 갱신은 호스트 cron(`renew` 후 `nginx -s reload`), HSTS `max-age=31536000`, 80 → 443 `301`
  - 로컬 운영 compose는 자체 서명 인증서로 실행(12-4 확인 방법), 4장에 `infra/certbot/` 추가
  - 도메인은 Cloudflare Registrar에서 구매(12-3)
