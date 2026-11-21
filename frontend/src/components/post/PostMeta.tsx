import Link from "next/link";

import { TagBadge } from "@/components/post/TagBadge";
import { formatDate } from "@/lib/date";
import { categoryHref } from "@/lib/taxonomy";
import type { components } from "@/types/api";

type Category = components["schemas"]["PostCategoryResponse"];

// 목록 카드와 상세가 같은 형식을 쓴다: 카테고리·발행일 줄, 그 아래 태그 줄.
export function PostMeta({
  category,
  publishedAt,
  tags,
  className,
}: {
  category: Category | null;
  publishedAt: string;
  tags: string[];
  className: string;
}) {
  return (
    <div className={`text-sm text-zinc-500 ${className}`}>
      <p>
        {category && (
          <>
            <Link href={categoryHref(category.slug, 1)} className="hover:underline">
              {category.name}
            </Link>
            {" · "}
          </>
        )}
        <time dateTime={publishedAt}>{formatDate(publishedAt)}</time>
      </p>
      {tags.length > 0 && (
        <p className="mt-1 flex flex-wrap gap-x-2">
          {tags.map((tag) => (
            <TagBadge key={tag} name={tag} />
          ))}
        </p>
      )}
    </div>
  );
}
