import { cacheLife } from "next/cache";

import { apiGet } from "@/lib/api/server";
import type { components } from "@/types/api";

type PublicCategory = components["schemas"]["CategoryResponse"];
type PublicTag = components["schemas"]["TagResponse"];

// 호출자는 요청 시점 API(params, searchParams) 뒤에서 호출해야 한다. 아니면 빌드 중에 실행되어 백엔드를 호출한다.
export async function getPublicCategories(): Promise<PublicCategory[]> {
  "use cache";
  cacheLife({ revalidate: 60 });
  return apiGet<PublicCategory[]>("/api/v1/categories");
}

export async function getPublicTags(): Promise<PublicTag[]> {
  "use cache";
  cacheLife({ revalidate: 60 });
  return apiGet<PublicTag[]>("/api/v1/tags");
}
