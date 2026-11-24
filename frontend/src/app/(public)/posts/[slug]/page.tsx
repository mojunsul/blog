import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { Comments } from "@/components/post/Comments";
import { PostMeta } from "@/components/post/PostMeta";
import { getPost } from "@/lib/api/posts";
import { SITE_DESCRIPTION, SITE_OPEN_GRAPH, siteUrl } from "@/lib/site";
import { isValidSlug } from "@/lib/slug";

export async function generateMetadata({ params }: PageProps<"/posts/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  if (!isValidSlug(slug)) return {};

  const post = await getPost(slug);
  if (post === null) return {};

  const description = post.summary ?? SITE_DESCRIPTION;
  const path = `/posts/${slug}`;
  return {
    metadataBase: siteUrl(),
    title: post.title,
    description,
    alternates: { canonical: path },
    openGraph: {
      ...SITE_OPEN_GRAPH,
      type: "article",
      title: post.title,
      description,
      url: path,
      publishedTime: post.publishedAt,
    },
  };
}

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
      <PostMeta category={post.category} publishedAt={post.publishedAt} tags={post.tags} className="mt-3 mb-8" />
      <MarkdownRenderer content={post.content} />
      <section aria-label="댓글" className="mt-12">
        <Comments key={post.id} postId={post.id} />
      </section>
    </article>
  );
}
