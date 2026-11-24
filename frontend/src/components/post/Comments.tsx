"use client";

import { useEffect, useRef } from "react";

const GISCUS = {
  repo: "mojunsul/blog",
  repoId: "R_kgDOVD5LSw",
  category: "Comments",
  categoryId: "DIC_kwDOVD5LS84DLw_6",
};

export function Comments({ postId }: { postId: number }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = ref.current;
    if (!container) return;

    const script = document.createElement("script");
    script.src = "https://giscus.app/client.js";
    script.async = true;
    script.crossOrigin = "anonymous";
    const attributes: Record<string, string> = {
      "data-repo": GISCUS.repo,
      "data-repo-id": GISCUS.repoId,
      "data-category": GISCUS.category,
      "data-category-id": GISCUS.categoryId,
      "data-mapping": "specific",
      "data-term": `post-${postId}`,
      "data-strict": "1",
      "data-reactions-enabled": "1",
      "data-emit-metadata": "0",
      "data-input-position": "bottom",
      "data-theme": "preferred_color_scheme",
      "data-lang": "ko",
      "data-loading": "lazy",
    };
    for (const [name, value] of Object.entries(attributes)) script.setAttribute(name, value);
    container.appendChild(script);

    // dev StrictMode의 effect 이중 실행과 재마운트 때 스크립트·iframe이 겹치지 않도록 비운다
    return () => container.replaceChildren();
  }, [postId]);

  return <div ref={ref} />;
}
