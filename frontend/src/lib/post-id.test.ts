import { describe, expect, it } from "vitest";

import { parsePostIdParam } from "@/lib/post-id";

describe("parsePostIdParam", () => {
  it.each([
    ["1", 1],
    ["12", 12],
    ["9007199254740991", 9007199254740991],
  ])("양의 정수 %s는 %i다", (value, expected) => {
    expect(parsePostIdParam(value)).toBe(expected);
  });

  it.each(["0", "01", "-1", "1.5", "1e2", "abc", "", " 1", "1 ", "9007199254740992", "99999999999999999999"])(
    "잘못된 값 '%s'는 null이다",
    (value) => {
      expect(parsePostIdParam(value)).toBeNull();
    },
  );
});
