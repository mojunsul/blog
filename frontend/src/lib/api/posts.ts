import { cacheLife } from "next/cache";

import { apiGet } from "@/lib/api/server";
import type { PageResponse, PostSummary } from "@/lib/api/types";

// 호출자는 요청 시점 API(searchParams) 뒤에서 호출해야 한다. 아니면 빌드 중에 실행되어 백엔드를 호출한다.
export async function getPosts(zeroBasedPage: number): Promise<PageResponse<PostSummary>> {
  "use cache";
  cacheLife({ revalidate: 60 });
  return apiGet<PageResponse<PostSummary>>(`/api/v1/posts?page=${zeroBasedPage}`);
}
