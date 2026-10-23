import Link from "next/link";
import { Suspense } from "react";

import { PostForm } from "@/components/admin/PostForm";
import { parsePostIdParam } from "@/lib/post-id";

export default function EditPostPage({ params }: PageProps<"/admin/posts/[id]/edit">) {
  return (
    <>
      <h1 className="mb-6 text-xl font-semibold">글 수정</h1>
      <Suspense fallback={<p>불러오는 중...</p>}>
        <EditPost params={params} />
      </Suspense>
    </>
  );
}

async function EditPost({ params }: { params: PageProps<"/admin/posts/[id]/edit">["params"] }) {
  const id = parsePostIdParam((await params).id);
  if (id === null) {
    return (
      <div className="flex flex-col items-start gap-2">
        <p>잘못된 주소입니다.</p>
        <Link href="/admin/posts">글 목록으로</Link>
      </div>
    );
  }
  return <PostForm id={id} />;
}
