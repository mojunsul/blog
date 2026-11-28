import Link from "next/link";

import { TagBadge } from "@/components/post/TagBadge";
import { formatDate } from "@/lib/date";
import { categoryHref } from "@/lib/taxonomy";
import type { components } from "@/types/api";

type Category = components["schemas"]["PostCategoryResponse"];

export function PostMeta({
  category,
  publishedAt,
  tags,
  viewCount,
  className,
}: {
  category: Category | null;
  publishedAt: string;
  tags: string[];
  viewCount?: number;
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
        {viewCount !== undefined && ` · 조회 ${viewCount.toLocaleString("ko-KR")}`}
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
