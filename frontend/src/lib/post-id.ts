const POST_ID_PATTERN = /^[1-9][0-9]*$/;

// 값이 API 경로에 그대로 들어가므로 숫자만 통과시킨다. 2^53 이상은 JS 수로 정확히 표현되지 않아 잘못된 주소로 본다.
export function parsePostIdParam(value: string): number | null {
  if (!POST_ID_PATTERN.test(value)) return null;
  const id = Number(value);
  return Number.isSafeInteger(id) ? id : null;
}
