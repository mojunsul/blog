// 생성 비용이 커서 모듈에서 한 번만 만든다. timeZone을 명시해 서버 시간대와 무관하게 KST로 표시한다.
const kstDateFormat = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

export function formatDate(iso: string): string {
  const { year, month, day } = Object.fromEntries(
    kstDateFormat.formatToParts(new Date(iso)).map((part) => [part.type, part.value]),
  );
  return `${year}.${month}.${day}`;
}
