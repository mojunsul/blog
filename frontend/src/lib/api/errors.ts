// 오류 응답은 OpenAPI 명세에 싣지 않으므로 생성 타입에 없다
interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code: string;
  errors: { field: string; reason: string }[];
}

const GENERIC_MESSAGE = "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | null,
    readonly detail: string,
    readonly errors: ProblemDetail["errors"],
  ) {
    super(detail);
    this.name = "ApiError";
  }
}

// 프록시(Nginx 502 HTML 등)가 만든 응답은 ProblemDetail이 아니므로, 본문을 사용자 메시지로 쓰지 않는다.
export function parseApiError(status: number, contentType: string | null, body: string): ApiError {
  if (contentType?.includes("application/problem+json")) {
    try {
      const problem: Partial<ProblemDetail> = JSON.parse(body);
      return new ApiError(
        status,
        typeof problem.code === "string" ? problem.code : null,
        typeof problem.detail === "string" ? problem.detail : GENERIC_MESSAGE,
        Array.isArray(problem.errors) ? problem.errors : [],
      );
    } catch {
      // 본문이 깨져도 HTTP 상태는 호출자에게 전달해야 하므로 파싱 오류를 던지지 않고 일반 오류로 넘어간다.
    }
  }
  return new ApiError(status, null, GENERIC_MESSAGE, []);
}
