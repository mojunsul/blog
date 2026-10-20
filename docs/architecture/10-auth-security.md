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
- 쿠키 이름·속성·`Max-Age`는 `CookieUtil`(`global/security`) 한 곳에서 만든다 (`ResponseCookie`, `Set-Cookie` 헤더). `CookieUtil`은 `@Component`이고 `AuthProperties`를 생성자로 받아 `Secure`를 정한다 (정적 메서드로 두면 컨트롤러가 설정을 따로 받아 넘겨야 한다). `Max-Age`는 토큰 수명과 같다 (Access 900초, Refresh 1209600초). 수명은 코드 상수로 두고 설정으로 빼지 않는다. 로그아웃용 만료 쿠키(`Max-Age=0`, 값 빈 문자열)도 여기서 만든다: 브라우저는 이름·`Path`가 같아야 지우므로 발급과 같은 코드를 거치게 한다.

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
4. 401 (Access 만료) → 프론트가 POST /api/v1/auth/refresh 호출 (X-XSRF-TOKEN 포함) 후 원래 요청 1회 재시도
   → 재발급도 401이면 /admin/login으로 이동
5. 로그아웃 → POST /api/v1/auth/logout: refresh 해시 삭제, 두 쿠키 `Max-Age=0`
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
  | `GET /api/v1/posts/**`, `GET /api/v1/categories`, `GET /api/v1/auth/csrf`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`, `POST /api/v1/auth/logout` | 모두 허용 (POST는 CSRF 검사를 받는다. 재발급은 Access 토큰이 만료된 상태로 부르고, 로그아웃은 만료 후에도 쿠키를 지울 수 있어야 해서 인증을 요구하지 않는다) |
  | `/api/v1/admin/**` | `ROLE_ADMIN` |
  | 그 외 | 인증 필요 |
  - `GET /api/v1/auth/me`는 '그 외'(인증 필요)에 걸리므로 따로 적지 않는다.
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
| 같은 이름이 여러 개 (Path가 다른 예전 쿠키가 남은 경우) | 요청에 실린 순서의 **첫 값만** 쓰고 나머지는 무시한다. 첫 값이 잘못됐으면 뒤 값이 유효해도 인증하지 않는다 (브라우저는 Path가 긴 쿠키를 먼저 보낸다. 현재 발급분은 `Path=/api`이고 더 긴 Path로 발급한 적이 없어서, 여러 개가 오는 경우는 더 짧은 Path(`/` 등)의 쿠키가 섞인 경우뿐이고 그때 첫 값이 현재 발급분이다. 제약: `access_token`의 Path를 `/api`보다 길게 바꾸지 않는다. 바꾼다면 예전 쿠키를 `Max-Age=0`으로 지운다. 안 그러면 예전 쿠키가 먼저 와 만료(최대 900초)까지 401이 반복된다) |
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
- `RefreshToken` 엔티티(`domain/auth/entity`): `admin`은 `@ManyToOne(fetch = LAZY)`, `refresh_token` 테이블에 `updated_at`이 없어 `BaseTimeEntity`를 상속하지 않는다 (`Admin`과 같다). `used_at`(`Instant`, null 허용)은 Repository의 조건부 UPDATE로만 채운다 (엔티티에 setter를 두지 않는다). 컬럼은 `V2__add_refresh_token_used_at.sql`로 추가한다.

**재발급 (`AuthService.refresh`)**
- `@Transactional(noRollbackFor = BusinessException.class)`. 재사용 감지에서 토큰을 지운 뒤 `INVALID_REFRESH_TOKEN`을 던지므로, 기본 롤백이면 삭제가 취소된다. 이 설정이 빠지면 감지가 조용히 무력화되므로 mock 테스트가 아닌 통합 테스트로 고정한다 (실제 트랜잭션과 DB 필요).
- 순서
  1. 쿠키 값이 `null`·`isBlank()`면 `INVALID_REFRESH_TOKEN`.
  2. 원문의 SHA-256 해시로 `RefreshTokenRepository.markUsed(hash, now)`를 부른다. `UPDATE ... SET used_at = :now WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now` 한 문장이고 영향 행 수를 돌려준다. 조회 후 갱신으로 나누면 동시 요청 둘이 모두 성공할 수 있어, 조건부 UPDATE의 행 잠금으로 하나만 성공하게 한다 (낙관적 락·`SELECT FOR UPDATE` 불필요).
  3. 영향 행이 1이면 성공: 그 행의 관리자로 Access 토큰과 새 Refresh 토큰(행 저장)을 만들어 `LoginResult`로 돌려준다 (로그인과 같은 생성 코드를 쓴다).
  4. 영향 행이 0이면 해시로 행을 찾는다. 행이 있고 `used_at`이 있으며 `expires_at`이 지나지 않았으면 재사용: 그 관리자의 Refresh 토큰을 모두 삭제하고 `WARN`(username만, 토큰 값·해시 금지)을 남긴 뒤 `INVALID_REFRESH_TOKEN`. 그 외(행 없음, 만료)는 로그 없이 `INVALID_REFRESH_TOKEN`.
- 만료된 쓴 토큰을 재사용으로 보지 않는 이유: 만료 후에는 어차피 못 쓰는 토큰이고, 정리 시각에 따라 같은 입력의 결과(전체 삭제 여부)가 달라지지 않게 한다.
- 로그인이 기존 행을 유지하므로(8-3) 재사용 감지의 전체 삭제는 다른 기기의 로그인도 끊는다. 의도한 동작이다.

**로그아웃 (`AuthService.logout`)**
- 쿠키 값이 있으면 해시로 `deleteByTokenHash`, 없으면 아무것도 하지 않는다. 결과와 관계없이 컨트롤러가 `204`와 만료 쿠키 둘을 내린다 (8-3). 로그를 남기지 않는다.

**만료 정리**
- `RefreshTokenCleaner`(`domain/auth`, `@Component`)의 `@Scheduled`가 매일 04:00 KST에 `deleteByExpiresAtBefore(now)`로 `expires_at`이 지난 행을 지운다 (`@Transactional`, `cron = "0 0 4 * * *"`, `zone = "Asia/Seoul"`로 서버 시간대에 기대지 않는다). 쓴 행도 같은 `expires_at`을 가지므로 따로 구분하지 않는다. 만료된 행은 재발급에서 이미 거절되므로 정리가 늦어도 보안에는 영향이 없고, 하루 한 번이면 행 수(관리자 1명, 로그인·재발급 횟수)에 충분하다. 로그는 남기지 않는다 (5장 로깅).
- `@EnableScheduling`은 `global/config/SchedulingConfig`에 둔다. 단일 서버라 중복 실행 방지(분산 락)는 두지 않는다.
