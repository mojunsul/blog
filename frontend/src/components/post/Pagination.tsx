import Link from "next/link";

interface PaginationProps {
  page: number;
  totalPages: number;
  hasNext: boolean;
  pageHref: (page: number) => string;
}

export function Pagination({ page, totalPages, hasNext, pageHref }: PaginationProps) {
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
