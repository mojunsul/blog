import rehypeShiki from "@shikijs/rehype";
import { MarkdownAsync } from "react-markdown";
import rehypeSlug from "rehype-slug";
import remarkGfm from "remark-gfm";

// raw HTML은 react-markdown 기본값(글자로 이스케이프)을 유지한다. rehype-raw, skipHtml, urlTransform 재정의는 XSS 방어를 깨므로 쓰지 않는다.
const shikiOptions = {
  themes: { light: "github-light", dark: "github-dark" },
  // 코드 블록에 나온 언어만 처음 쓸 때 불러온다. 기본값은 내장 언어 전체를 한 번에 불러와 작은 서버의 첫 렌더링이 느려진다.
  langs: [],
  lazy: true,
  defaultLanguage: "text",
  fallbackLanguage: "text",
};

// react-markdown에는 className prop이 없어 본문 스타일을 여기서 감싼다.
export async function MarkdownRenderer({ content }: { content: string }) {
  return (
    <div className="prose dark:prose-invert max-w-none">
      <MarkdownAsync remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSlug, [rehypeShiki, shikiOptions]]}>
        {content}
      </MarkdownAsync>
    </div>
  );
}
