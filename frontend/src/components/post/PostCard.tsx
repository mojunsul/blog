import Link from "next/link";

import type { components } from "@/types/api";
import { formatDate } from "@/lib/date";

type PostSummary = components["schemas"]["PostSummaryResponse"];

export function PostCard({ post }: { post: PostSummary }) {
  return (
    <article className="py-6">
      <h2 className="text-xl font-semibold">
        <Link href={`/posts/${post.slug}`} className="hover:underline">
          {post.title}
        </Link>
      </h2>
      {post.summary && <p className="mt-2 text-zinc-600 dark:text-zinc-400">{post.summary}</p>}
      <p className="mt-3 text-sm text-zinc-500">
        {post.category && <span>{post.category.name} · </span>}
        <time dateTime={post.publishedAt}>{formatDate(post.publishedAt)}</time>
      </p>
    </article>
  );
}
