// API q 상한(8-1)과 같다. 둘 다 UTF-16 단위로 센다.
const MAX_QUERY_LENGTH = 100;

// ""는 검색어 없음, null은 잘못된 값이다. 잘못된 값은 API 400이 오류 화면이 되지 않게 호출 전에 걸러 notFound() 처리한다.
export function parseSearchQuery(value: string | string[] | undefined): string | null {
  if (value === undefined) return "";
  if (typeof value !== "string") return null;
  const query = value.trim();
  return query.length <= MAX_QUERY_LENGTH ? query : null;
}

export function searchHref(q: string, page: number): string {
  const params = new URLSearchParams({ q });
  if (page > 1) params.set("page", String(page));
  return `/search?${params}`;
}
