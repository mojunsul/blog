import { LoginForm } from "@/components/admin/LoginForm";

export default function AdminLoginPage() {
  return (
    <main className="mx-auto w-full max-w-sm flex-1 px-4 py-16">
      <h1 className="mb-6 text-xl font-semibold">관리자 로그인</h1>
      <LoginForm />
    </main>
  );
}
