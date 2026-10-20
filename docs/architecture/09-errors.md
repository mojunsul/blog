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

