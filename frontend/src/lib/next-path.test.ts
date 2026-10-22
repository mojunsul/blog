import { describe, expect, it } from "vitest";

import { parseNextParam } from "@/lib/next-path";

describe("parseNextParam", () => {
  it.each(["/admin", "/admin/", "/admin/posts", "/admin/posts/1/edit", "/admin?x=1", "/admin/posts?page=2"])(
    "/admin 아래 경로 '%s'는 그대로 돌려준다",
    (path) => {
      expect(parseNextParam(path)).toBe(path);
    },
  );

  it.each([
    null,
    "",
    "//evil.com",
    "https://evil.com",
    "/administrator",
    "/posts/1",
    "admin",
    "/admin/login",
    "/admin/login?next=/admin",
    "/admin/loginx",
    "/admin\\evil",
    "/admin/\nx",
    "/admin/\tx",
    "/admin/\u0000",
  ])("허용하지 않는 값 %j는 /admin이다", (raw) => {
    expect(parseNextParam(raw)).toBe("/admin");
  });
});
