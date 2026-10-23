import { notFound } from "next/navigation";
import { Suspense } from "react";

import { Pagination } from "@/components/post/Pagination";
import { PostList } from "@/components/post/PostList";
import { getPosts } from "@/lib/api/posts";
import { parsePageParam } from "@/lib/pagination";

// 첫 페이지는 파라미터 없는 /로 연결해 같은 내용이 여러 URL로 색인되지 않게 한다.
const pageHref = (page: number) => (page === 1 ? "/" : `/?page=${page}`);

export default function HomePage({ searchParams }: PageProps<"/">) {
  return (
    <Suspense fallback={<p>불러오는 중...</p>}>
      <Posts searchParams={searchParams} />
    </Suspense>
  );
}

async function Posts({ searchParams }: { searchParams: PageProps<"/">["searchParams"] }) {
  const page = parsePageParam((await searchParams).page);
  if (page === null) notFound();

  const posts = await getPosts(page - 1);
  if (page > 1 && posts.content.length === 0) notFound();
  if (posts.content.length === 0) return <p>아직 작성된 글이 없습니다.</p>;

  return (
    <>
      <PostList posts={posts.content} />
      <Pagination page={page} totalPages={posts.totalPages} hasNext={posts.hasNext} pageHref={pageHref} />
    </>
  );
}
