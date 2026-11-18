import { describe, expect, it } from "vitest";

import { parseTags, validateTags } from "@/lib/tags";

const COUNT = "태그는 10개 이하여야 합니다";
const LENGTH = "태그는 50자 이하여야 합니다";
const PATTERN = "태그는 문자·숫자·하이픈만 쓸 수 있습니다";

describe("parseTags", () => {
  it("쉼표로 나누고 앞뒤 공백을 지우고 소문자로 바꾼다", () => {
    expect(parseTags(" Spring , JWT ")).toEqual(["spring", "jwt"]);
  });

  it("빈 조각을 버린다", () => {
    expect(parseTags("a,, ,b,")).toEqual(["a", "b"]);
    expect(parseTags("")).toEqual([]);
  });

  it("중복은 처음 나온 순서를 남기고 지운다", () => {
    expect(parseTags("Spring, jwt, spring")).toEqual(["spring", "jwt"]);
  });

  it("소문자로 바꾸면 길어지는 문자는 바꾼 값을 돌려준다", () => {
    expect(parseTags("İ")).toEqual(["i̇"]);
  });
});

describe("validateTags", () => {
  it("10개와 50자는 통과한다", () => {
    expect(validateTags(Array.from({ length: 10 }, (_, i) => `t${i}`))).toBeNull();
    expect(validateTags(["x".repeat(50)])).toBeNull();
  });

  it("11개는 개수 메시지다", () => {
    expect(validateTags(Array.from({ length: 11 }, (_, i) => `t${i}`))).toBe(COUNT);
  });

  it("51자는 길이 메시지이고 소문자로 바꾼 뒤 길이를 센다", () => {
    expect(validateTags(["x".repeat(51)])).toBe(LENGTH);
    expect(validateTags(parseTags("İ".repeat(26)))).toBe(LENGTH);
    expect(validateTags(parseTags("İ".repeat(25)))).toBe(PATTERN);
    expect(validateTags(parseTags("İ".repeat(25) + "x"))).toBe(LENGTH);
  });

  it.each(["한글-태그", "123", "node-js", "cpp"])("'%s'는 통과한다", (tag) => {
    expect(validateTags([tag])).toBeNull();
  });

  it.each(["-a", "a-", "a--b", "a_b", "a b"])("'%s'는 패턴 메시지다", (tag) => {
    expect(validateTags([tag])).toBe(PATTERN);
  });

  it("개수가 먼저이고 그다음은 항목 순서대로 길이, 패턴이다", () => {
    const eleven = ["x".repeat(51), ...Array.from({ length: 10 }, (_, i) => `t${i}`)];
    expect(validateTags(eleven)).toBe(COUNT);
    expect(validateTags(["a_b", "x".repeat(51)])).toBe(PATTERN);
    expect(validateTags(["x".repeat(51) + "_", "a_b"])).toBe(LENGTH);
  });
});
