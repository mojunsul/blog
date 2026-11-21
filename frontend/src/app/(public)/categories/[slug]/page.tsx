import { notFound } from "next/navigation";
import { Suspense } from "react";

import { Pagination } from "@/components/post/Pagination";
import { PostList } from "@/components/post/PostList";
import { getPosts } from "@/lib/api/posts";
import { getPublicCategories } from "@/lib/api/taxonomy";
import { parsePageParam } from "@/lib/pagination";
import { categoryHref } from "@/lib/taxonomy";

export default function CategoryPage({ params, searchParams }: PageProps<"/categories/[slug]">) {
  return (
    <Suspense fallback={<p>불러오는 중...</p>}>
      <CategoryPosts params={params} searchParams={searchParams} />
    </Suspense>
  );
}

async function CategoryPosts({
  params,
  searchParams,
}: {
  params: PageProps<"/categories/[slug]">["params"];
  searchParams: PageProps<"/categories/[slug]">["searchParams"];
}) {
  const { slug } = await params;
  const page = parsePageParam((await searchParams).page);
  if (page === null) notFound();

  const category = (await getPublicCategories()).find((c) => c.slug === slug);
  if (!category) notFound();

  const posts = await getPosts(page - 1, { category: category.slug });
  if (page > 1 && posts.content.length === 0) notFound();
  if (posts.content.length === 0) return <p>{category.name} 카테고리에 아직 글이 없습니다.</p>;

  return (
    <>
      <h1 className="mb-6 text-xl font-semibold">
        {category.name} 카테고리 글 {posts.totalElements}건
      </h1>
      <PostList posts={posts.content} />
      <Pagination
        page={page}
        totalPages={posts.totalPages}
        hasNext={posts.hasNext}
        pageHref={(target) => categoryHref(category.slug, target)}
      />
    </>
  );
}
