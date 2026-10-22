import { PostCard } from "@/components/post/PostCard";
import type { components } from "@/types/api";

type PostSummary = components["schemas"]["PostSummaryResponse"];

export function PostList({ posts }: { posts: PostSummary[] }) {
  return (
    <div className="divide-y divide-zinc-200 dark:divide-zinc-800">
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  );
}
