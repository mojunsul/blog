"use client";

import { logout } from "@/lib/api/auth";

export function LogoutButton() {
  async function handleClick() {
    try {
      await logout();
    } catch {
      // 실패하면 이동하지 않고 버튼을 남겨 다시 누를 수 있게 한다. 오류 문구는 #76에서 레이아웃과 함께 정한다.
      return;
    }
    // 전체 새로고침으로 화면에 남은 인증 상태를 모두 버린다.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.assign("/admin/login");
  }

  return (
    <button type="button" onClick={handleClick} className="rounded border border-zinc-300 px-3 py-1 dark:border-zinc-700">
      로그아웃
    </button>
  );
}
