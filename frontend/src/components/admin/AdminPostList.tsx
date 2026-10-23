"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { Pagination } from "@/components/post/Pagination";
import { deletePost, getAdminPosts } from "@/lib/api/admin-posts";
import { ApiError } from "@/lib/api/errors";
import { formatDate } from "@/lib/date";
import type { components } from "@/types/api";

type AdminPostPage = components["schemas"]["PageResponseAdminPostSummaryResponse"];
type AdminPostSummary = components["schemas"]["AdminPostSummaryResponse"];
type PostStatus = AdminPostSummary["status"];

type LoadResult = { key: string; data: AdminPostPage } | { key: string; failed: true };

const STATUS_LABELS: Record<PostStatus, string> = { DRAFT: "작성 중", PUBLISHED: "발행됨" };
const FILTERS: { label: string; status?: PostStatus }[] = [
  { label: "전체" },
  { label: "작성 중", status: "DRAFT" },
  { label: "발행됨", status: "PUBLISHED" },
];
const LOAD_FAILURE_MESSAGE = "글 목록을 불러오지 못했습니다. 새로고침해 주세요.";
const DELETE_FAILURE_MESSAGE = "삭제하지 못했습니다. 잠시 후 다시 시도해 주세요.";

function listHref(status: PostStatus | undefined, page = 1): string {
  const params = new URLSearchParams();
  if (status) params.set("status", status);
  if (page > 1) params.set("page", String(page));
  const query = params.toString();
  return query ? `/admin/posts?${query}` : "/admin/posts";
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

function isPostNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404 && error.code === "POST_NOT_FOUND";
}

export function AdminPostList({ page, status }: { page: number; status?: PostStatus }) {
  const [reloadCount, setReloadCount] = useState(0);
  const [result, setResult] = useState<LoadResult | null>(null);
  const [deleting, setDeleting] = useState(false);
  // effect에서 동기로 "불러오는 중" 상태를 넣지 않고, 결과의 요청 키가 지금 요청과 다르면 불러오는 중으로 본다.
  const requestKey = `${status ?? ""}:${page}:${reloadCount}`;
  const loaded = result?.key === requestKey ? result : null;

  useEffect(() => {
    let ignored = false;
    getAdminPosts({ page: page - 1, status }).then(
      (data) => {
        if (!ignored) setResult({ key: requestKey, data });
      },
      (error) => {
        // 401은 client.ts가 로그인 화면으로 이동시키는 중이라 오류 문구 없이 "불러오는 중..."을 유지한다.
        if (!ignored && !isUnauthorized(error)) setResult({ key: requestKey, failed: true });
      },
    );
    return () => {
      ignored = true;
    };
  }, [page, status, requestKey]);

  async function handleDelete(post: AdminPostSummary) {
    if (!window.confirm(`"${post.title}" 글을 삭제할까요? 되돌릴 수 없습니다.`)) return;
    setDeleting(true);
    try {
      await deletePost(post.id);
    } catch (error) {
      if (isUnauthorized(error)) return;
      // POST_NOT_FOUND는 다른 탭 등에서 이미 지운 경우라 바라던 상태이므로 문구 없이 목록만 갱신한다.
      if (!isPostNotFound(error)) {
        setDeleting(false);
        window.alert(DELETE_FAILURE_MESSAGE);
        return;
      }
    }
    setDeleting(false);
    setReloadCount((count) => count + 1);
  }

  return (
    <>
      <nav aria-label="상태 필터" className="mb-4 flex gap-4 text-sm">
        {FILTERS.map((filter) => (
          <Link
            key={filter.label}
            href={listHref(filter.status)}
            aria-current={filter.status === status ? "page" : undefined}
            className="aria-[current=page]:font-semibold aria-[current=page]:underline"
          >
            {filter.label}
          </Link>
        ))}
      </nav>
      {renderContent()}
    </>
  );

  function renderContent() {
    if (!loaded) return <p>불러오는 중...</p>;
    if ("failed" in loaded) return <p>{LOAD_FAILURE_MESSAGE}</p>;

    const { content, totalPages, hasNext } = loaded.data;
    if (content.length === 0 && page === 1) return <p>글이 없습니다.</p>;
    if (content.length === 0) {
      return (
        <div className="flex flex-col items-start gap-2">
          <p>이 페이지에는 글이 없습니다.</p>
          <Link href={listHref(status)}>첫 페이지로</Link>
        </div>
      );
    }

    return (
      <>
        <table className="w-full text-left text-sm">
          <thead className="border-b border-zinc-200 dark:border-zinc-800">
            <tr>
              <th scope="col" className="py-2">제목</th>
              <th scope="col">상태</th>
              <th scope="col">카테고리</th>
              <th scope="col">발행일</th>
              <th scope="col">수정일</th>
              <th scope="col">
                <span className="sr-only">삭제</span>
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
            {content.map((post) => (
              <tr key={post.id}>
                <td className="py-2">
                  <Link href={`/admin/posts/${post.id}/edit`} className="hover:underline">
                    {post.title}
                  </Link>
                </td>
                <td>{STATUS_LABELS[post.status]}</td>
                <td>{post.category?.name ?? "-"}</td>
                <td>{post.publishedAt ? formatDate(post.publishedAt) : "-"}</td>
                <td>{formatDate(post.updatedAt)}</td>
                <td>
                  <button
                    type="button"
                    onClick={() => handleDelete(post)}
                    disabled={deleting}
                    className="text-red-600 disabled:opacity-50"
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <Pagination page={page} totalPages={totalPages} hasNext={hasNext} pageHref={(target) => listHref(status, target)} />
      </>
    );
  }
}
