import Link from "next/link";

import { PostMeta } from "@/components/post/PostMeta";
import type { components } from "@/types/api";

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
      <PostMeta category={post.category} publishedAt={post.publishedAt} tags={post.tags} className="mt-3" />
    </article>
  );
}
