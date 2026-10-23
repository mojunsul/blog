"use client";

import { useState } from "react";

import { logout } from "@/lib/api/auth";

const FAILURE_MESSAGE = "로그아웃하지 못했습니다. 다시 시도해 주세요.";

export function LogoutButton() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleClick() {
    setError(null);
    setPending(true);
    try {
      await logout();
    } catch {
      setError(FAILURE_MESSAGE);
      setPending(false);
      return;
    }
    // 전체 새로고침으로 화면에 남은 인증 상태를 모두 버린다.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign("/admin/login");
  }

  return (
    <div className="flex items-center gap-2">
      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}
      <button
        type="button"
        onClick={handleClick}
        disabled={pending}
        className="rounded border border-zinc-300 px-3 py-1 disabled:opacity-50 dark:border-zinc-700"
      >
        로그아웃
      </button>
    </div>
  );
}
