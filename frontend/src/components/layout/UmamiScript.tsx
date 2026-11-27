import { connection } from "next/server";
import Script from "next/script";

export default async function UmamiScript() {
  // 정적 셸에서 읽으면 빌드 때 값(CI에서는 빈 값)이 박히므로 요청 시점에 읽는다
  await connection();
  const websiteId = process.env.UMAMI_WEBSITE_ID;
  if (!websiteId) return null;
  return <Script src="/stats/script.js" data-website-id={websiteId} strategy="afterInteractive" />;
}
