// 실제 도메인은 저장소에 두지 않으므로 기본값 없이 실행 시점에 읽는다. 운영 설정 누락이 엉뚱한 주소로 숨지 않게 한다.
export function getSiteUrl(): string {
  const url = process.env.SITE_URL;
  if (!url) throw new Error("SITE_URL 환경변수가 설정되지 않았습니다.");
  return url;
}
