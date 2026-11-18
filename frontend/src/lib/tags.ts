export const MAX_TAGS = 10;
export const MAX_TAG_LENGTH = 50;
export const TAG_PATTERN = /^[\p{L}\p{N}]+(?:-[\p{L}\p{N}]+)*$/u;

export function parseTags(value: string): string[] {
  const tags = value
    .split(",")
    .map((piece) => piece.trim().toLowerCase())
    .filter((tag) => tag !== "");
  return [...new Set(tags)];
}

export function validateTags(tags: string[]): string | null {
  if (tags.length > MAX_TAGS) return `태그는 ${MAX_TAGS}개 이하여야 합니다`;
  for (const tag of tags) {
    if (tag.length > MAX_TAG_LENGTH) return `태그는 ${MAX_TAG_LENGTH}자 이하여야 합니다`;
    if (!TAG_PATTERN.test(tag)) return "태그는 문자·숫자·하이픈만 쓸 수 있습니다";
  }
  return null;
}
