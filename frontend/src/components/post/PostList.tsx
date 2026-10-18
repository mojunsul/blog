import { PostCard } from "@/components/post/PostCard";
import type { PostSummary } from "@/lib/api/types";

export function PostList({ posts }: { posts: PostSummary[] }) {
  return (
    <div className="divide-y divide-zinc-200 dark:divide-zinc-800">
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  );
}
