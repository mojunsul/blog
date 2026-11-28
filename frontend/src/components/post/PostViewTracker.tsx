"use client";

import { useEffect } from "react";

// 조회수는 부가 기능이라 실패(404·429·네트워크 오류)는 모두 무시한다
export function PostViewTracker({ slug }: { slug: string }) {
  useEffect(() => {
    fetch(`/api/v1/posts/${slug}/views`, { method: "POST" }).catch(() => {});
  }, [slug]);

  return null;
}
