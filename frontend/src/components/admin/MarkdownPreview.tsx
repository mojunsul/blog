import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";

// 미리보기마다 서버 왕복이나 브라우저 Shiki 번들을 들이는 비용에 비해 하이라이팅 색만 달라서 동기 렌더링으로 둔다.
export function MarkdownPreview({ content }: { content: string }) {
  if (content.trim() === "") return <p>미리 볼 내용이 없습니다.</p>;
  return (
    <div className="prose dark:prose-invert max-w-none">
      {/* rehype-raw·urlTransform 재정의·skipHtml 변경은 raw HTML과 javascript: 링크를 살려 XSS가 되므로 쓰지 않는다. */}
      <Markdown remarkPlugins={[remarkGfm]}>{content}</Markdown>
    </div>
  );
}
