## 5. 백엔드 구조

**도메인(기능) 단위 패키지**로 구성한다.

```
backend/src/main/java/io/github/mojunsul/blog/
├ BlogApplication.java
├ global/
│  ├ config/          # QuerydslConfig, JpaAuditingConfig, OpenApiConfig, ClockConfig, PasswordEncoderConfig, SchedulingConfig, AdminProperties, AuthProperties
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
   │  └ dto/          # PostSearchRequest, AdminPostSearchRequest, PostRequest(작성·수정 공통), PostCreateResponse, PostSummaryResponse, PostDetailResponse,
   │                  # AdminPostSummaryResponse, AdminPostDetailResponse ...
   ├ category/
   │  ├ controller/   # CategoryController(공개), AdminCategoryController(관리자)
   │  ├ service/      # CategoryService
   │  ├ repository/   # CategoryRepository
   │  ├ entity/       # Category
   │  └ dto/          # CategoryRequest, CategoryResponse, AdminCategoryResponse
   ├ tag/             # Tag, PostTag
   └ auth/            # AuthController, AuthService, Admin·AdminRepository, RefreshToken·RefreshTokenRepository,
                      # scheduler/RefreshTokenCleaner (@Scheduled)

backend/src/main/resources/
├ application.yaml          # 공통
├ application-local.yaml    # 로컬 DB, Swagger 활성화
├ application-prod.yaml     # 운영 (값은 환경변수 주입, Swagger 비활성화)
└ db/migration/
   ├ V1__init_schema.sql
   └ V2__add_refresh_token_used_at.sql

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
- **OpenAPI 명세는 프론트 타입 생성의 입력이다** (11장 'API 타입 생성'). springdoc 기본 출력은 응답 필드에 `required`·nullable이 없어 생성 타입이 모든 필드를 `?`(선택)로 만들고, 객체 참조의 nullable은 어노테이션만으로 올바르게 표현되지 않는다 (`@Schema(nullable = true)`를 `$ref` 필드에 붙이면 `$ref`와 `"type": "null"`이 한 스키마에 같이 나와 생성기가 `null`을 버린다, springdoc 3.1.1·OpenAPI 3.1에서 확인). 그래서 아래처럼 한다.
  - 응답 DTO에서 `null`이 될 수 있는 필드에만 `@Schema(nullable = true)`를 붙인다. 그 외 응답 필드는 항상 값이 있다 (`tags`는 항상 배열).
    - 공개 `PostSummaryResponse`·`PostDetailResponse`: `summary`, `thumbnailUrl`, `category`
    - `AdminPostSummaryResponse`: `category`, `publishedAt`
    - `AdminPostDetailResponse`: `summary`, `thumbnailUrl`, `categoryId`, `category`, `publishedAt`
  - `global/config/OpenApiConfig`의 `OpenApiCustomizer` 빈이 이름에 `Response`가 들어간 스키마(`PageResponseXxx` 포함)에만 다음을 적용한다. `Request` 스키마는 검증 어노테이션이 `required`를 이미 만들므로 건드리지 않는다.
    - 모든 property를 `required`에 넣는다 (응답은 `null`이어도 필드를 내려 주므로 키가 빠지는 경우가 없다. 응답 DTO마다 `requiredMode`를 붙이는 반복을 피한다).
    - `$ref`이면서 `types`에 `null`이 있는 property는 `oneOf: [{$ref}, {type: null}]`로 바꾼다 (스칼라의 `["string", "null"]`은 springdoc이 올바르게 만들므로 그대로 둔다).
  - 이 두 규칙은 Spring 없는 단위 테스트(`OpenApiConfigTest`)로 고정한다: 커스터마이저를 직접 호출해 `Response`의 `required`, `$ref`+`null`의 `oneOf` 변환, 스칼라 nullable 유지, `Request` 스키마 불변을 확인한다. `@Schema(nullable = true)`를 빠뜨렸는지는 이 테스트가 모르므로, 생성 타입(`api.d.ts`)의 `| null`을 위 목록과 대조해 확인한다 (완료 조건).
  - 에러 응답(`ProblemDetail`의 `code`·`errors` 확장)과 상태 코드(201·204), `@ModelAttribute` 쿼리 파라미터 펼침은 명세에 반영하지 않는다. 프론트는 `components.schemas`만 쓰고(`paths`는 쓰지 않는다), 반영하려면 엔드포인트마다 어노테이션이 필요해 얻는 것에 비해 반복이 크다. Swagger UI의 "Try it out"은 이 때문에 정확하지 않을 수 있다.
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
  - 카테고리 생성·수정·삭제는 `CategoryService`가 성공한 뒤 `INFO`로 `id`(생성·수정은 `slug`도)만 남긴다. `name`은 로그에 넣지 않는다 (8-4).
  - 글 생성·수정·삭제는 `PostService`가 같은 방식으로 남긴다 (`id`, 생성·수정은 `slug`도). `title`은 로그에 넣지 않는다 (8-4).
  - 재사용 감지는 `AuthService`가 `WARN`으로 남긴다(username만). 이어서 던지는 `INVALID_REFRESH_TOKEN`의 `GlobalExceptionHandler` `WARN`과 별개이고, 재사용이 아닌 재발급 실패는 후자만 남는다.
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

