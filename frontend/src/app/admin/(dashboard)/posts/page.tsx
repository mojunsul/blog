import Link from "next/link";
import { Suspense } from "react";

import { AdminPostList } from "@/components/admin/AdminPostList";
import { parsePageParam } from "@/lib/pagination";
import { parseStatusParam } from "@/lib/post-status";

export default function AdminPostsPage({ searchParams }: PageProps<"/admin/posts">) {
  return (
    <>
      <div className="mb-2 flex items-center justify-between">
        <h1 className="text-xl font-semibold">글</h1>
        <Link href="/admin/posts/new" className="rounded border border-zinc-300 px-3 py-1 dark:border-zinc-700">
          새 글
        </Link>
      </div>
      <p className="mb-6 text-sm text-zinc-500">
        저장·삭제한 내용은 1분이 지난 뒤 공개 페이지를 열면 갱신되며, 한 번 더 새로고침해야 보일 수 있습니다.
      </p>
      <Suspense fallback={<p>불러오는 중...</p>}>
        <AdminPosts searchParams={searchParams} />
      </Suspense>
    </>
  );
}

// 공개 목록과 같은 해석 함수를 같은 입력 형태로 쓰려고 searchParams는 서버에서 읽고, 데이터는 클라이언트에서 부른다.
async function AdminPosts({ searchParams }: { searchParams: PageProps<"/admin/posts">["searchParams"] }) {
  const params = await searchParams;
  const page = parsePageParam(params.page);
  const status = parseStatusParam(params.status);
  if (page === null || status === null) {
    return (
      <div className="flex flex-col items-start gap-2">
        <p>잘못된 주소입니다.</p>
        <Link href="/admin/posts">글 목록으로</Link>
      </div>
    );
  }
  return <AdminPostList page={page} status={status} />;
}
