import Link from "next/link";

import { AdminAuthGuard } from "@/components/admin/AdminAuthGuard";
import { LogoutButton } from "@/components/admin/LogoutButton";

// AdminAuthGuard가 인증 확인 전에는 children을 그리지 않는 것이 설계라 instant navigation 검증 대상이 아니다.
export const instant = false;

export default function AdminDashboardLayout({ children }: LayoutProps<"/admin">) {
  return (
    <AdminAuthGuard>
      <header className="border-b border-zinc-200 dark:border-zinc-800">
        <div className="mx-auto flex max-w-4xl items-center gap-4 px-4 py-4">
          <Link href="/admin/posts">글</Link>
          <Link href="/admin/categories">카테고리</Link>
          <div className="ml-auto">
            <LogoutButton />
          </div>
        </div>
      </header>
      <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8">{children}</main>
    </AdminAuthGuard>
  );
}
