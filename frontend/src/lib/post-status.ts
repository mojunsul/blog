import type { components } from "@/types/api";

type PostStatus = components["schemas"]["AdminPostSummaryResponse"]["status"];

const STATUSES: PostStatus[] = ["DRAFT", "PUBLISHED"];

// API는 앞뒤 공백·빈 값도 받아 주지만, 화면 링크가 만드는 값만 허용해 고친 URL은 API 호출 전에 거른다.
export function parseStatusParam(value: string | string[] | undefined): PostStatus | undefined | null {
  if (value === undefined) return undefined;
  if (typeof value !== "string") return null;
  return STATUSES.find((status) => status === value) ?? null;
}
