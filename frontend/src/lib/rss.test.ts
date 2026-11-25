import { describe, expect, it } from "vitest";

import { buildRss, RSS_ALTERNATE_TYPES, RSS_PATH } from "@/lib/rss";

const channel = { title: "Blog", description: "개인 개발 블로그" };
const post = (over: Record<string, unknown> = {}) =>
  ({
    id: 1,
    title: "제목",
    slug: "a",
    summary: "요약",
    category: { name: "Backend" },
    publishedAt: "2026-10-11T01:00:00Z",
    ...over,
  }) as never;

describe("buildRss", () => {
  it("채널 값과 atom:link를 절대 주소로 만든다", () => {
    const xml = buildRss("https://blog.test", channel, []);
    expect(xml).toContain('<?xml version="1.0" encoding="UTF-8"?>');
    expect(xml).toContain('<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">');
    expect(xml).toContain("<title>Blog</title>");
    expect(xml).toContain("<link>https://blog.test/</link>");
    expect(xml).toContain("<description>개인 개발 블로그</description>");
    expect(xml).toContain("<language>ko</language>");
    expect(xml).toContain('<atom:link href="https://blog.test/rss.xml" rel="self" type="application/rss+xml"/>');
    expect(xml).not.toContain("<item>");
  });

  it("항목 요소를 순서대로 만들고 pubDate는 RFC 822 형식이다", () => {
    const xml = buildRss("https://blog.test/", channel, [post()]);
    expect(xml).toContain(
      "<item><title>제목</title><link>https://blog.test/posts/a</link>" +
        '<guid isPermaLink="false">post-1</guid><description>요약</description>' +
        "<category>Backend</category><pubDate>Sun, 11 Oct 2026 01:00:00 GMT</pubDate></item>",
    );
  });

  it("summary와 category가 null이면 요소를 두지 않는다", () => {
    const xml = buildRss("https://blog.test", channel, [post({ summary: null, category: null })]);
    const item = xml.slice(xml.indexOf("<item>"));
    expect(item).not.toContain("<description>");
    expect(item).not.toContain("<category>");
  });

  it("앞 20개만 넣는다", () => {
    const posts = Array.from({ length: 21 }, (_, i) => post({ id: i, slug: `s${i}` }));
    const xml = buildRss("https://blog.test", channel, posts);
    expect(xml.match(/<item>/g)).toHaveLength(20);
    expect(xml).not.toContain("post-20");
  });

  it("특수 문자를 한 번만 이스케이프한다", () => {
    const xml = buildRss("https://blog.test", channel, [
      post({ title: `A & B <C> "D" 'E'`, summary: "&lt;", category: { name: "<x>" } }),
    ]);
    expect(xml).toContain("<title>A &amp; B &lt;C&gt; &quot;D&quot; &apos;E&apos;</title>");
    expect(xml).toContain("<description>&amp;lt;</description>");
    expect(xml).toContain("<category>&lt;x&gt;</category>");
  });

  it("XML에서 쓸 수 없는 문자를 지우고 줄바꿈·탭은 유지한다", () => {
    const xml = buildRss("https://blog.test", channel, [
      post({ title: "a\uffffb", summary: "c\u0001d\uffff\n\te", category: { name: "f\uffffg" } }),
    ]);
    expect(xml).toContain("<title>ab</title>");
    expect(xml).toContain("<description>cd\n\te</description>");
    expect(xml).toContain("<category>fg</category>");
  });
});

describe("RSS 상수", () => {
  it("alternate types는 RSS_PATH를 가리킨다", () => {
    expect(RSS_ALTERNATE_TYPES).toEqual({ "application/rss+xml": RSS_PATH });
  });
});
