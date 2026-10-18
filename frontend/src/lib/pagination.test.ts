import { describe, expect, it } from "vitest";

import { parsePageParam } from "@/lib/pagination";

describe("parsePageParam", () => {
  it("값이 없으면 1페이지다", () => {
    expect(parsePageParam(undefined)).toBe(1);
  });

  it.each([
    ["1", 1],
    ["2", 2],
    ["10", 10],
    ["10000", 10000],
  ])("양의 정수 %s는 %i페이지다", (value, expected) => {
    expect(parsePageParam(value)).toBe(expected);
  });

  it.each(["0", "-1", "1.5", "abc", "", "01", "1e2", " 2", "2 ", "10001", "214748366", "99999999999999999999"])("잘못된 값 '%s'는 null이다", (value) => {
    expect(parsePageParam(value)).toBeNull();
  });

  it("같은 키가 여러 개면 null이다", () => {
    expect(parsePageParam(["1", "2"])).toBeNull();
  });
});
