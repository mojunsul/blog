import Link from "next/link";

interface PaginationProps {
  page: number;
  totalPages: number;
  hasNext: boolean;
}

// 첫 페이지는 파라미터 없는 /로 연결해 같은 내용이 여러 URL로 색인되지 않게 한다.
const pageHref = (page: number) => (page === 1 ? "/" : `/?page=${page}`);

export function Pagination({ page, totalPages, hasNext }: PaginationProps) {
  if (totalPages <= 1) return null;

  return (
    <nav aria-label="페이지 이동" className="mt-8 flex items-center justify-between text-sm">
      {page > 1 ? <Link href={pageHref(page - 1)}>이전</Link> : <span />}
      <span>
        {page} / {totalPages}
      </span>
      {hasNext ? <Link href={pageHref(page + 1)}>다음</Link> : <span />}
    </nav>
  );
}
