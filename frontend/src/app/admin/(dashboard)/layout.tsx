import { AdminAuthGuard } from "@/components/admin/AdminAuthGuard";

// AdminAuthGuard가 인증 확인 전에는 children을 그리지 않는 것이 설계라 instant navigation 검증 대상이 아니다.
export const instant = false;

export default function AdminDashboardLayout({ children }: LayoutProps<"/admin">) {
  return <AdminAuthGuard>{children}</AdminAuthGuard>;
}
