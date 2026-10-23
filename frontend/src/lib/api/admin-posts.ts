import type { components } from "@/types/api";

import { apiFetch } from "@/lib/api/client";

type AdminPostPage = components["schemas"]["PageResponseAdminPostSummaryResponse"];
type AdminPostSearchRequest = components["schemas"]["AdminPostSearchRequest"];

export function getAdminPosts({ page, status }: { page: number; status?: AdminPostSearchRequest["status"] }): Promise<AdminPostPage> {
  const params = new URLSearchParams({ page: String(page) });
  if (status) params.set("status", status);
  return apiFetch(`/admin/posts?${params}`);
}

export function deletePost(id: number): Promise<void> {
  return apiFetch(`/admin/posts/${id}`, { method: "DELETE" });
}
