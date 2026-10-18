import rehypeShiki from "@shikijs/rehype";
import { MarkdownAsync } from "react-markdown";
import rehypeSlug from "rehype-slug";
import remarkGfm from "remark-gfm";

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
      {/* rehype-raw는 raw HTML을 실제 요소로 만들고 urlTransform을 재정의하면 javascript: 링크 차단이 풀려 XSS가 되므로 쓰지 않는다. skipHtml도 기본값을 유지해 HTML을 글자로 보여 준다. */}
      <MarkdownAsync remarkPlugins={[remarkGfm]} rehypePlugins={[rehypeSlug, [rehypeShiki, shikiOptions]]}>
        {content}
      </MarkdownAsync>
    </div>
  );
}
