import { describe, expect, it } from "vitest";

import { formatDate } from "@/lib/date";

describe("formatDate", () => {
  it.each([
    ["2026-10-11T14:59:59Z", "2026.10.11"],
    ["2026-10-11T15:00:00Z", "2026.10.12"],
    ["2026-10-31T15:00:00Z", "2026.11.01"],
    ["2026-12-31T15:00:00Z", "2027.01.01"],
    ["2028-02-28T15:00:00Z", "2028.02.29"],
    ["2027-02-28T15:00:00Z", "2027.03.01"],
    ["2026-10-11T15:00:00.123Z", "2026.10.12"],
    ["2026-10-12T00:00:00+09:00", "2026.10.12"],
  ])("%s는 KST 기준 %s로 표시한다", (iso, expected) => {
    expect(formatDate(iso)).toBe(expected);
  });

  it("잘못된 입력은 RangeError를 던진다", () => {
    expect(() => formatDate("not-a-date")).toThrow(RangeError);
  });
});
