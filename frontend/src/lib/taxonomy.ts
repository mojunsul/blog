const withPage = (path: string, page: number) => (page > 1 ? `${path}?page=${page}` : path);

// slug는 영문 소문자·숫자·하이픈뿐이라 인코딩하지 않는다.
export function categoryHref(slug: string, page: number): string {
  return withPage(`/categories/${slug}`, page);
}

export function tagHref(name: string, page: number): string {
  return withPage(`/tags/${encodeURIComponent(name)}`, page);
}

// Next의 params는 퍼센트 인코딩을 풀지 않은 값이다. 디코딩이 실패하면 오류 화면 대신 notFound()가 되도록 null을 돌려준다.
export function parseTagNameParam(value: string): string | null {
  try {
    return decodeURIComponent(value);
  } catch {
    return null;
  }
}
