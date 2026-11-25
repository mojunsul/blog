import { connection } from "next/server";

import { getAllPublishedPosts } from "@/lib/api/posts";
import { buildRss } from "@/lib/rss";
import { SITE_DESCRIPTION, SITE_NAME, siteUrl } from "@/lib/site";

export async function GET(): Promise<Response> {
  await connection();
  const baseUrl = siteUrl().href;
  const posts = await getAllPublishedPosts();
  const xml = buildRss(baseUrl, { title: SITE_NAME, description: SITE_DESCRIPTION }, posts);
  return new Response(xml, { headers: { "Content-Type": "application/rss+xml; charset=utf-8" } });
}
