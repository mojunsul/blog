import type { components } from "@/types/api";

import { apiFetch } from "@/lib/api/client";

type AdminPostPage = components["schemas"]["PageResponseAdminPostSummaryResponse"];
type AdminPostSearchRequest = components["schemas"]["AdminPostSearchRequest"];
type AdminPostDetail = components["schemas"]["AdminPostDetailResponse"];
type PostRequest = components["schemas"]["PostRequest"];
type PostCreateResponse = components["schemas"]["PostCreateResponse"];

export function getAdminPosts({ page, status }: { page: number; status?: AdminPostSearchRequest["status"] }): Promise<AdminPostPage> {
  const params = new URLSearchParams({ page: String(page) });
  if (status) params.set("status", status);
  return apiFetch(`/admin/posts?${params}`);
}

export function deletePost(id: number): Promise<void> {
  return apiFetch(`/admin/posts/${id}`, { method: "DELETE" });
}

export function getAdminPost(id: number): Promise<AdminPostDetail> {
  return apiFetch(`/admin/posts/${id}`);
}

export function createPost(body: PostRequest): Promise<PostCreateResponse> {
  return apiFetch("/admin/posts", { method: "POST", body });
}

export function updatePost(id: number, body: PostRequest): Promise<AdminPostDetail> {
  return apiFetch(`/admin/posts/${id}`, { method: "PUT", body });
}
