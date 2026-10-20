import { notFound } from "next/navigation";
import { Suspense } from "react";

import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { getPost } from "@/lib/api/posts";
import { formatDate } from "@/lib/date";
import { isValidSlug } from "@/lib/slug";

export default function PostPage({ params }: PageProps<"/posts/[slug]">) {
  return (
    <Suspense fallback={<p>불러오는 중...</p>}>
      <Post params={params} />
    </Suspense>
  );
}

async function Post({ params }: { params: PageProps<"/posts/[slug]">["params"] }) {
  const { slug } = await params;
  if (!isValidSlug(slug)) notFound();

  const post = await getPost(slug);
  if (post === null) notFound();

  return (
    <article>
      <h1 className="text-3xl font-semibold">{post.title}</h1>
      <p className="mt-3 mb-8 text-sm text-zinc-500">
        {post.category && <span>{post.category.name} · </span>}
        <time dateTime={post.publishedAt}>{formatDate(post.publishedAt)}</time>
      </p>
      <MarkdownRenderer content={post.content} />
    </article>
  );
}
