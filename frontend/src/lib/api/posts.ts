import { cacheLife } from "next/cache";

import { apiGet } from "@/lib/api/server";
import type { PageResponse, PostSummary } from "@/lib/api/types";

// page는 API 기준(0부터)이다. 호출자는 요청 시점 API(searchParams) 뒤에서 호출해야 빌드 중 실행되지 않는다.
export async function getPosts(page: number): Promise<PageResponse<PostSummary>> {
  "use cache";
  cacheLife({ revalidate: 60 });
  return apiGet<PageResponse<PostSummary>>(`/api/v1/posts?page=${page}`);
}
