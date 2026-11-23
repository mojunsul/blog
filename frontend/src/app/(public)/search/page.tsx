import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { Pagination } from "@/components/post/Pagination";
import { PostList } from "@/components/post/PostList";
import { searchPosts } from "@/lib/api/posts";
import { parsePageParam } from "@/lib/pagination";
import { parseSearchQuery, searchHref } from "@/lib/search";

export const metadata: Metadata = { title: "검색", robots: { index: false } };

export default function SearchPage({ searchParams }: PageProps<"/search">) {
  return (
    <Suspense fallback={<p>불러오는 중...</p>}>
      <SearchResults searchParams={searchParams} />
    </Suspense>
  );
}

async function SearchResults({ searchParams }: { searchParams: PageProps<"/search">["searchParams"] }) {
  const params = await searchParams;
  const page = parsePageParam(params.page);
  const q = parseSearchQuery(params.q);
  if (page === null || q === null) notFound();
  if (q === "") return <p>검색어를 입력하세요.</p>;

  const posts = await searchPosts(q, page - 1);
  if (page > 1 && posts.content.length === 0) notFound();
  if (posts.content.length === 0) return <p>‘{q}’에 대한 검색 결과가 없습니다.</p>;

  return (
    <>
      <h1 className="mb-6 text-xl font-semibold">
        ‘{q}’ 검색 결과 {posts.totalElements}건
      </h1>
      <PostList posts={posts.content} />
      <Pagination
        page={page}
        totalPages={posts.totalPages}
        hasNext={posts.hasNext}
        pageHref={(target) => searchHref(q, target)}
      />
    </>
  );
}
