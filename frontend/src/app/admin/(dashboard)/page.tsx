import { redirect } from "next/navigation";

// 로그인 후 기본 이동 경로(/admin)는 그대로 두고 여기서 글 목록으로 넘긴다.
export default function AdminHomePage() {
  redirect("/admin/posts");
}
