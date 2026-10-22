import { describe, expect, it } from "vitest";

import { readCookie } from "@/lib/cookie";

describe("readCookie", () => {
  it("이름이 같은 쿠키의 값을 돌려준다", () => {
    expect(readCookie("a=1; XSRF-TOKEN=abc; b=2", "XSRF-TOKEN")).toBe("abc");
  });

  it("쿠키가 하나뿐이어도 읽는다", () => {
    expect(readCookie("XSRF-TOKEN=abc", "XSRF-TOKEN")).toBe("abc");
  });

  it("이름이 정확히 같은 쿠키만 읽는다", () => {
    expect(readCookie("X-XSRF-TOKEN=abc; XSRF-TOKEN-2=def", "XSRF-TOKEN")).toBeNull();
  });

  it("없으면 null이다", () => {
    expect(readCookie("", "XSRF-TOKEN")).toBeNull();
    expect(readCookie("a=1", "XSRF-TOKEN")).toBeNull();
  });

  it("값이 빈 문자열이면 null이다", () => {
    expect(readCookie("XSRF-TOKEN=; a=1", "XSRF-TOKEN")).toBeNull();
  });

  it("같은 이름이 여러 개면 첫 값이다", () => {
    expect(readCookie("XSRF-TOKEN=first; XSRF-TOKEN=second", "XSRF-TOKEN")).toBe("first");
  });

  it("값에 =가 있으면 첫 = 뒤를 모두 값으로 본다", () => {
    expect(readCookie("XSRF-TOKEN=a=b==", "XSRF-TOKEN")).toBe("a=b==");
  });

  it("디코딩하지 않는다", () => {
    expect(readCookie("XSRF-TOKEN=a%20b", "XSRF-TOKEN")).toBe("a%20b");
  });
});
