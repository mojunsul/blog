import { describe, expect, it } from "vitest";

import { parseStatusParam } from "@/lib/post-status";

describe("parseStatusParam", () => {
  it("값이 없으면 전체(undefined)다", () => {
    expect(parseStatusParam(undefined)).toBeUndefined();
  });

  it.each(["DRAFT", "PUBLISHED"])("%s는 그 값이다", (value) => {
    expect(parseStatusParam(value)).toBe(value);
  });

  it.each(["", "draft", "Published", " DRAFT", "DRAFT ", "ARCHIVED", "null"])("잘못된 값 '%s'는 null이다", (value) => {
    expect(parseStatusParam(value)).toBeNull();
  });

  it("같은 키가 여러 개면 null이다", () => {
    expect(parseStatusParam(["DRAFT", "PUBLISHED"])).toBeNull();
  });
});
