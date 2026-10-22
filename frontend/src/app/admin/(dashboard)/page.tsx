import { LogoutButton } from "@/components/admin/LogoutButton";

// 임시 화면: #76에서 /admin/posts로 이동하도록 교체
export default function AdminHomePage() {
  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8">
      <h1 className="mb-4 text-xl font-semibold">관리자</h1>
      <LogoutButton />
    </main>
  );
}
