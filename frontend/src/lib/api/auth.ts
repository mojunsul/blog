import type { components } from "@/types/api";

import { apiFetch } from "@/lib/api/client";

type AdminResponse = components["schemas"]["AdminResponse"];
type LoginRequest = components["schemas"]["LoginRequest"];

export function login(request: LoginRequest): Promise<AdminResponse> {
  return apiFetch("/auth/login", { method: "POST", body: request });
}

export function logout(): Promise<void> {
  return apiFetch("/auth/logout", { method: "POST" });
}

export function getMe(): Promise<AdminResponse> {
  return apiFetch("/auth/me");
}
