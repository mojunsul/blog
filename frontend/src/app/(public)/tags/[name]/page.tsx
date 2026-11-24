import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { Pagination } from "@/components/post/Pagination";
import { PostList } from "@/components/post/PostList";
import { getPosts } from "@/lib/api/posts";
import { getPublicTags } from "@/lib/api/taxonomy";
import { parsePageParam } from "@/lib/pagination";
import { parseTagNameParam, tagHref } from "@/lib/taxonomy";

export async function generateMetadata({ params }: PageProps<"/tags/[name]">): Promise<Metadata> {
  const decoded = parseTagNameParam((await params).name);
  if (decoded === null) return {};

  const tag = (await getPublicTags()).find((t) => t.name === decoded);
  if (!tag) return {};
  return { title: `#${tag.name} 태그` };
}

export default function TagPage({ params, searchParams }: PageProps<"/tags/[name]">) {
  return (
    <Suspense fallback={<p>불러오는 중...</p>}>
      <TagPosts params={params} searchParams={searchParams} />
    </Suspense>
  );
}

async function TagPosts({
  params,
  searchParams,
}: {
  params: PageProps<"/tags/[name]">["params"];
  searchParams: PageProps<"/tags/[name]">["searchParams"];
}) {
  const rawName = (await params).name;
  const page = parsePageParam((await searchParams).page);
  const decoded = parseTagNameParam(rawName);
  if (page === null || decoded === null) notFound();

  const tag = (await getPublicTags()).find((t) => t.name === decoded);
  if (!tag) notFound();

  const posts = await getPosts(page - 1, { tag: tag.name });
  if (page > 1 && posts.content.length === 0) notFound();
  if (posts.content.length === 0) return <p>#{tag.name} 태그가 달린 글이 없습니다.</p>;

  return (
    <>
      <h1 className="mb-6 text-xl font-semibold">
        #{tag.name} 태그 글 {posts.totalElements}건
      </h1>
      <PostList posts={posts.content} />
      <Pagination
        page={page}
        totalPages={posts.totalPages}
        hasNext={posts.hasNext}
        pageHref={(target) => tagHref(tag.name, target)}
      />
    </>
  );
}
