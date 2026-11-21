import { describe, expect, it } from "vitest";

import { categoryHref, parseTagNameParam, tagHref } from "@/lib/taxonomy";

describe("categoryHref", () => {
  it("첫 페이지는 page를 붙이지 않는다", () => {
    expect(categoryHref("backend", 1)).toBe("/categories/backend");
  });

  it("2페이지부터 page를 붙인다", () => {
    expect(categoryHref("backend", 2)).toBe("/categories/backend?page=2");
  });
});

describe("tagHref", () => {
  it("첫 페이지는 page를 붙이지 않는다", () => {
    expect(tagHref("spring", 1)).toBe("/tags/spring");
  });

  it("2페이지부터 page를 붙인다", () => {
    expect(tagHref("spring", 2)).toBe("/tags/spring?page=2");
  });

  it("한글 태그는 인코딩한다", () => {
    expect(tagHref("스프링", 1)).toBe("/tags/%EC%8A%A4%ED%94%84%EB%A7%81");
  });

  it("하이픈은 그대로 둔다", () => {
    expect(tagHref("node-js", 1)).toBe("/tags/node-js");
  });
});

describe("parseTagNameParam", () => {
  it("인코딩된 한글을 디코딩한다", () => {
    expect(parseTagNameParam("%EC%8A%A4%ED%94%84%EB%A7%81")).toBe("스프링");
  });

  it("%20을 공백으로 디코딩한다", () => {
    expect(parseTagNameParam("a%20b")).toBe("a b");
  });

  it("대소문자는 바꾸지 않는다", () => {
    expect(parseTagNameParam("Spring")).toBe("Spring");
  });

  it.each(["%E0%A4%A", "%"])("잘못된 인코딩(%s)은 null", (value) => {
    expect(parseTagNameParam(value)).toBeNull();
  });
});
