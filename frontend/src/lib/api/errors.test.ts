import { describe, expect, it } from "vitest";

import { ApiError, parseApiError } from "@/lib/api/errors";

describe("parseApiError", () => {
  it("ProblemDetail 응답은 code, detail, errors를 파싱한다", () => {
    const body = JSON.stringify({
      type: "about:blank",
      title: "Bad Request",
      status: 400,
      detail: "입력값이 올바르지 않습니다.",
      instance: "/api/v1/posts",
      code: "INVALID_INPUT",
      errors: [{ field: "size", reason: "size는 50 이하여야 합니다" }],
    });

    const error = parseApiError(400, "application/problem+json", body);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(400);
    expect(error.code).toBe("INVALID_INPUT");
    expect(error.detail).toBe("입력값이 올바르지 않습니다.");
    expect(error.errors).toEqual([{ field: "size", reason: "size는 50 이하여야 합니다" }]);
  });

  it("Content-Type에 charset이 붙어도 ProblemDetail로 파싱한다", () => {
    const body = JSON.stringify({ status: 404, detail: "글을 찾을 수 없습니다.", code: "POST_NOT_FOUND", errors: [] });

    const error = parseApiError(404, "application/problem+json;charset=UTF-8", body);

    expect(error.code).toBe("POST_NOT_FOUND");
    expect(error.status).toBe(404);
  });

  it("ProblemDetail이 아닌 응답은 code를 null로 하고 일반 메시지를 쓴다", () => {
    const error = parseApiError(502, "text/html", "<html>Bad Gateway</html>");

    expect(error.status).toBe(502);
    expect(error.code).toBeNull();
    expect(error.detail).not.toContain("<html>");
    expect(error.detail).not.toBe("");
    expect(error.errors).toEqual([]);
  });

  it("Content-Type이 없어도 일반 오류로 변환한다", () => {
    const error = parseApiError(500, null, "");

    expect(error.code).toBeNull();
    expect(error.errors).toEqual([]);
  });

  it("ProblemDetail이라고 했지만 본문이 깨져 있으면 일반 오류로 변환한다", () => {
    const error = parseApiError(500, "application/problem+json", "{");

    expect(error.status).toBe(500);
    expect(error.code).toBeNull();
    expect(error.errors).toEqual([]);
  });
});
