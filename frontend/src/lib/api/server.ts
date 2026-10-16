import "server-only";

import { parseApiError } from "@/lib/api/errors";

export async function apiGet<T>(path: string): Promise<T> {
  // 기본값으로 대신하면 운영 설정 누락이 엉뚱한 주소로의 연결 시도에 가려진다.
  const baseUrl = process.env.API_INTERNAL_URL;
  if (!baseUrl) {
    throw new Error("API_INTERNAL_URL이 설정되지 않았습니다. frontend/.env.example을 .env.local로 복사하세요.");
  }

  const response = await fetch(`${baseUrl}${path}`);
  if (!response.ok) {
    throw parseApiError(response.status, response.headers.get("content-type"), await response.text());
  }
  return (await response.json()) as T;
}
