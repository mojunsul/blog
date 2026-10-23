import { z } from "zod";

import { MAX_SLUG_LENGTH, SLUG_PATTERN } from "@/lib/slug";

const hasNoNul = (value: string) => !value.includes("\u0000");

// 서버 규칙(PostRequest)과 메시지를 같게 유지한다. 서버도 title·summary·thumbnailUrl만 앞뒤 공백을 지운다.
export const postSchema = z.object({
  title: z
    .string()
    .trim()
    .min(1, "제목을 입력하세요")
    .max(200, "제목은 200자 이하여야 합니다")
    .regex(/^\P{Cc}+$/u, "제목에 제어 문자를 쓸 수 없습니다"),
  slug: z
    .string()
    .min(1, "slug를 입력하세요")
    .max(MAX_SLUG_LENGTH, `slug는 ${MAX_SLUG_LENGTH}자 이하여야 합니다`)
    .regex(SLUG_PATTERN, "slug는 영문 소문자·숫자·하이픈만 쓸 수 있습니다"),
  categoryId: z.string(),
  status: z.enum(["DRAFT", "PUBLISHED"]),
  summary: z
    .string()
    .trim()
    .max(500, "요약은 500자 이하여야 합니다")
    .refine(hasNoNul, "요약에 NUL 문자를 쓸 수 없습니다"),
  thumbnailUrl: z
    .string()
    .trim()
    .max(500, "썸네일 URL은 500자 이하여야 합니다")
    .refine(
      (value) => value === "" || /^https?:\/\/[^\s\p{Cc}]+$/u.test(value),
      "썸네일 URL은 공백 없는 http(s) 주소여야 합니다",
    ),
  content: z
    .string()
    .refine((value) => value.trim() !== "", "본문을 입력하세요")
    .max(100000, "본문은 100,000자 이하여야 합니다")
    .refine(hasNoNul, "본문에 NUL 문자를 쓸 수 없습니다"),
});

export type PostValues = z.infer<typeof postSchema>;
