import { describe, expect, it } from "vitest";

import { parseSearchQuery, searchHref } from "@/lib/search";

describe("parseSearchQuery", () => {
  it.each([undefined, "", "   "])("없음·빈 값·공백만(%j)은 빈 문자열", (value) => {
    expect(parseSearchQuery(value)).toBe("");
  });

  it("앞뒤 공백을 지운다", () => {
    expect(parseSearchQuery("  spring boot ")).toBe("spring boot");
  });

  it("같은 키가 여러 개면 null", () => {
    expect(parseSearchQuery(["a", "b"])).toBeNull();
  });

  it("100자는 통과하고 101자는 null", () => {
    expect(parseSearchQuery("a".repeat(100))).toBe("a".repeat(100));
    expect(parseSearchQuery("a".repeat(101))).toBeNull();
  });

  it("앞뒤 공백을 포함해 102자여도 내용이 100자면 통과", () => {
    expect(parseSearchQuery(` ${"a".repeat(100)} `)).toBe("a".repeat(100));
  });
});

describe("searchHref", () => {
  it("첫 페이지는 page를 붙이지 않는다", () => {
    expect(searchHref("spring", 1)).toBe("/search?q=spring");
  });

  it("2페이지부터 page를 붙인다", () => {
    expect(searchHref("spring", 2)).toBe("/search?q=spring&page=2");
  });

  it("특수 문자와 한글을 인코딩한다", () => {
    expect(searchHref("100% & #a 글", 1)).toBe("/search?q=100%25+%26+%23a+%EA%B8%80");
  });
});
