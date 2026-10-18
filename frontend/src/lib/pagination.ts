const PAGE_PATTERN = /^[1-9][0-9]*$/;

// 잘못된 값은 첫 페이지로 보정하지 않고 null로 돌려 호출자가 notFound() 처리하게 한다.
export function parsePageParam(value: string | string[] | undefined): number | null {
  if (value === undefined) return 1;
  if (typeof value !== "string" || !PAGE_PATTERN.test(value)) return null;
  return Number(value);
}
