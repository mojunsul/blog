import { describe, expect, it } from "vitest";

import { buildSitemap } from "@/lib/sitemap";

const post = (slug: string, updatedAt: string) => ({ slug, updatedAt }) as never;
const category = (slug: string, postCount: number) => ({ slug, postCount }) as never;
const tag = (name: string) => ({ name }) as never;

describe("buildSitemap", () => {
  it("홈, 글, 카테고리, 태그 순서로 절대 주소를 만든다", () => {
    const result = buildSitemap(
      "https://blog.test",
      [post("a", "2026-10-11T01:00:00Z")],
      [category("backend", 2)],
      [tag("spring")],
    );
    expect(result.map((entry) => entry.url)).toEqual([
      "https://blog.test/",
      "https://blog.test/posts/a",
      "https://blog.test/categories/backend",
      "https://blog.test/tags/spring",
    ]);
  });

  it("SITE_URL 끝의 /가 있어도 같은 주소를 만든다", () => {
    expect(buildSitemap("https://blog.test/", [post("a", "2026-10-11T01:00:00Z")], [], []).map((e) => e.url)).toEqual([
      "https://blog.test/",
      "https://blog.test/posts/a",
    ]);
  });

  it("글의 lastModified는 updatedAt이고 나머지는 없다", () => {
    const result = buildSitemap("https://blog.test", [post("a", "2026-10-11T01:00:00Z")], [category("b", 1)], [tag("t")]);
    expect(result[0].lastModified).toBeUndefined();
    expect(result[1].lastModified).toBe("2026-10-11T01:00:00Z");
    expect(result[2].lastModified).toBeUndefined();
    expect(result[3].lastModified).toBeUndefined();
  });

  it("글이 없는 카테고리는 뺀다", () => {
    const result = buildSitemap("https://blog.test", [], [category("infra", 0), category("backend", 1)], []);
    expect(result.map((entry) => entry.url)).toEqual(["https://blog.test/", "https://blog.test/categories/backend"]);
  });

  it("한글 태그는 퍼센트 인코딩한다", () => {
    const result = buildSitemap("https://blog.test", [], [], [tag("스프링")]);
    expect(result[1].url).toBe("https://blog.test/tags/%EC%8A%A4%ED%94%84%EB%A7%81");
  });

  it("모두 비면 홈 하나다", () => {
    expect(buildSitemap("https://blog.test", [], [], [])).toEqual([{ url: "https://blog.test/" }]);
  });
});
