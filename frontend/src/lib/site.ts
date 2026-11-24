import "server-only";

export const SITE_NAME = "Blog";
export const SITE_DESCRIPTION = "개인 개발 블로그";
export const SITE_OPEN_GRAPH = { siteName: SITE_NAME, locale: "ko_KR", type: "website" } as const;

export function siteUrl(): URL {
  // 기본값으로 대신하면 운영 설정 누락이 엉뚱한 canonical 주소에 가려진다.
  const url = process.env.SITE_URL;
  if (!url) {
    throw new Error("SITE_URL이 설정되지 않았습니다. frontend/.env.example을 .env.local로 복사하세요.");
  }
  return new URL(url);
}
