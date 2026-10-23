import { z } from "zod";

import { SLUG_PATTERN } from "@/lib/slug";

// 서버 규칙(CategoryRequest)과 메시지를 같게 유지한다. slug 상한은 글 slug(MAX_SLUG_LENGTH)가 아니라 category.slug 컬럼 길이다.
export const categorySchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "카테고리 이름을 입력하세요")
    .max(50, "카테고리 이름은 50자 이하여야 합니다")
    .regex(/^\P{Cc}+$/u, "카테고리 이름에 제어 문자를 쓸 수 없습니다"),
  slug: z
    .string()
    .min(1, "slug를 입력하세요")
    .max(100, "slug는 100자 이하여야 합니다")
    .regex(SLUG_PATTERN, "slug는 영문 소문자·숫자·하이픈만 쓸 수 있습니다"),
});

export type CategoryValues = z.infer<typeof categorySchema>;
