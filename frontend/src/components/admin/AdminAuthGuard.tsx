"use client";

import { useEffect, useState } from "react";

import { getMe } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/errors";

type Status = "checking" | "authenticated" | "failed";

// proxy.ts는 Path=/api 쿠키를 /admin 요청에서 볼 수 없어 쓰지 못하므로, 클라이언트에서 인증을 확인한다.
export function AdminAuthGuard({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<Status>("checking");

  useEffect(() => {
    getMe().then(
      () => setStatus("authenticated"),
      (error) => {
        // 401은 client.ts가 로그인 화면으로 이동시키는 중이라 오류 문구를 띄우지 않고 "확인 중..."을 유지한다.
        if (!(error instanceof ApiError && error.status === 401)) {
          setStatus("failed");
        }
      },
    );
  }, []);

  if (status === "authenticated") {
    return children;
  }
  return <p className="p-4">{status === "checking" ? "확인 중..." : "로그인 상태를 확인하지 못했습니다. 새로고침해 주세요."}</p>;
}
