"use client";

import { useEffect, useState } from "react";

import { getMe } from "@/lib/api/auth";

type Status = "checking" | "authenticated" | "failed";

// proxy.ts는 Path=/api 쿠키를 /admin 요청에서 볼 수 없어 쓰지 못하므로, 클라이언트에서 인증을 확인한다.
export function AdminAuthGuard({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>("checking");

  useEffect(() => {
    // 401은 client.ts가 재발급 후 로그인 화면으로 이동시키므로 여기서는 그 외 오류만 실패로 본다.
    getMe().then(
      () => setStatus("authenticated"),
      () => setStatus("failed"),
    );
  }, []);

  if (status === "authenticated") {
    return children;
  }
  return <p className="p-4">{status === "checking" ? "확인 중..." : "로그인 상태를 확인하지 못했습니다. 새로고침해 주세요."}</p>;
}
