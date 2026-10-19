import { describe, expect, it } from "vitest";

import { isValidSlug } from "@/lib/slug";

describe("isValidSlug", () => {
  it.each(["a", "spring-security-jwt", "post-1", "123", "a".repeat(200)])("'%s'는 유효하다", (slug) => {
    expect(isValidSlug(slug)).toBe(true);
  });

  it.each([
    "",
    "Spring-Jwt",
    "a.b",
    "a/b",
    "a%2Fb",
    "-a",
    "a-",
    "a--b",
    "a b",
    "한글",
    "..",
    "a".repeat(201),
  ])("'%s'는 유효하지 않다", (slug) => {
    expect(isValidSlug(slug)).toBe(false);
  });
});
