import { cacheLife } from "next/cache";

import { ApiError } from "@/lib/api/errors";
import { apiGet } from "@/lib/api/server";
import type { components } from "@/types/api";

type PostSummaryPage = components["schemas"]["PageResponsePostSummaryResponse"];
type PostDetail = components["schemas"]["PostDetailResponse"];

// 호출자는 요청 시점 API(searchParams) 뒤에서 호출해야 한다. 아니면 빌드 중에 실행되어 백엔드를 호출한다.
export async function getPosts(zeroBasedPage: number): Promise<PostSummaryPage> {
  "use cache";
  cacheLife({ revalidate: 60 });
  return apiGet<PostSummaryPage>(`/api/v1/posts?page=${zeroBasedPage}`);
}

// 호출자는 slug가 isValidSlug를 통과한 뒤에 호출해야 한다. 규칙 밖 값은 API 경로를 바꾸거나 400을 일으킬 수 있다.
// 'use cache' 경계를 넘으면 ApiError가 일반 Error로 바뀌어 status를 잃으므로, 404는 여기서 null로 바꿔 돌려준다.
export async function getPost(slug: string): Promise<PostDetail | null> {
  "use cache";
  cacheLife({ revalidate: 60 });
  try {
    return await apiGet<PostDetail>(`/api/v1/posts/${slug}`);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}
