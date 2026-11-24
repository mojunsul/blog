import type { components } from "@/types/api";

type Post = Pick<components["schemas"]["PostSummaryResponse"], "id" | "title" | "slug" | "summary" | "publishedAt"> & {
  category: { name: string } | null;
};

export const RSS_PATH = "/rss.xml";
export const RSS_ALTERNATE_TYPES = { "application/rss+xml": RSS_PATH };

const MAX_ITEMS = 20;
const ENTITIES: Record<string, string> = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&apos;" };

// XML 1.0 금지 문자가 하나라도 있으면 피드 전체가 파싱 오류라 값마다 지운다.
function escapeXml(value: string): string {
  return value
    .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F￾￿]/g, "")
    .replace(/[&<>"']/g, (c) => ENTITIES[c]);
}

export function buildRss(
  siteUrl: string,
  channel: { title: string; description: string },
  posts: Post[],
): string {
  const abs = (path: string) => escapeXml(new URL(path, siteUrl).href);
  const el = (name: string, value: string) => `<${name}>${escapeXml(value)}</${name}>`;
  const items = posts.slice(0, MAX_ITEMS).map(
    (post) =>
      "<item>" +
      el("title", post.title) +
      `<link>${abs(`/posts/${post.slug}`)}</link>` +
      `<guid isPermaLink="false">post-${post.id}</guid>` +
      (post.summary === null ? "" : el("description", post.summary)) +
      (post.category === null ? "" : el("category", post.category.name)) +
      el("pubDate", new Date(post.publishedAt).toUTCString()) +
      "</item>",
  );
  return (
    '<?xml version="1.0" encoding="UTF-8"?>' +
    '<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom"><channel>' +
    el("title", channel.title) +
    `<link>${abs("/")}</link>` +
    el("description", channel.description) +
    "<language>ko</language>" +
    `<atom:link href="${abs(RSS_PATH)}" rel="self" type="application/rss+xml"/>` +
    items.join("") +
    "</channel></rss>"
  );
}
