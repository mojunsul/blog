// API의 page 상한(9999)에 1을 더한 값이다. 넘는 값은 API 호출 전에 걸러 API 400이 오류 화면이 되지 않게 한다.
const MAX_PAGE = 10000;
const PAGE_PATTERN = /^[1-9][0-9]*$/;

// 잘못된 값은 첫 페이지로 보정하지 않고 null로 돌려 호출자가 notFound() 처리하게 한다.
export function parsePageParam(value: string | string[] | undefined): number | null {
  if (value === undefined) return 1;
  if (typeof value !== "string" || !PAGE_PATTERN.test(value)) return null;
  const page = Number(value);
  return page <= MAX_PAGE ? page : null;
}
