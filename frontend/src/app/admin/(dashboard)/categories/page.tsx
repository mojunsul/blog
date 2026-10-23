import { AdminCategoryManager } from "@/components/admin/AdminCategoryManager";

export default function AdminCategoriesPage() {
  return (
    <>
      <h1 className="mb-2 text-xl font-semibold">카테고리</h1>
      <p className="mb-6 text-sm text-zinc-500">
        변경한 내용은 1분이 지난 뒤 공개 페이지를 열면 갱신되며, 한 번 더 새로고침해야 보일 수 있습니다.
      </p>
      <AdminCategoryManager />
    </>
  );
}
