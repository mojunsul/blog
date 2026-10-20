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
