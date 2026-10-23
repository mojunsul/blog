import type { components } from "@/types/api";

import { apiFetch } from "@/lib/api/client";

type Category = components["schemas"]["CategoryResponse"];
type CategoryRequest = components["schemas"]["CategoryRequest"];
type AdminCategory = components["schemas"]["AdminCategoryResponse"];

// 서버 컴포넌트용 posts.ts는 60초 캐시라 방금 만든 카테고리가 늦게 보이므로 브라우저에서 공개 API를 직접 부른다.
export function getCategories(): Promise<Category[]> {
  return apiFetch("/categories");
}

export function createCategory(body: CategoryRequest): Promise<AdminCategory> {
  return apiFetch("/admin/categories", { method: "POST", body });
}

export function updateCategory(id: number, body: CategoryRequest): Promise<AdminCategory> {
  return apiFetch(`/admin/categories/${id}`, { method: "PUT", body });
}

export function deleteCategory(id: number): Promise<void> {
  return apiFetch(`/admin/categories/${id}`, { method: "DELETE" });
}
