import Link from "next/link";
import { IconChevronLeft, IconChevronRight } from "./icons";

type Props = {
  page: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
  hrefFor: (page: number) => string;
};

const linkClass =
  "inline-flex min-h-11 items-center gap-1.5 rounded-[3px] border border-line bg-white px-4 text-sm font-medium text-charcoal transition-colors hover:border-forest hover:text-forest";
const disabledClass =
  "inline-flex min-h-11 items-center gap-1.5 rounded-[3px] border border-line/60 px-4 text-sm font-medium text-muted/50";

export function Pagination({ page, totalPages, hasPrevious, hasNext, hrefFor }: Props) {
  if (totalPages <= 1) return null;

  return (
    <nav
      aria-label="Pagination"
      className="mt-12 flex flex-wrap items-center justify-between gap-4 border-t border-line pt-6"
    >
      <p className="text-sm text-muted">
        Page {page + 1} of {totalPages}
      </p>
      <div className="flex items-center gap-2">
        {hasPrevious ? (
          <Link href={hrefFor(page - 1)} className={linkClass} rel="prev">
            <IconChevronLeft className="h-4 w-4" />
            Previous
          </Link>
        ) : (
          <span className={disabledClass} aria-disabled="true">
            <IconChevronLeft className="h-4 w-4" />
            Previous
          </span>
        )}
        {hasNext ? (
          <Link href={hrefFor(page + 1)} className={linkClass} rel="next">
            Next
            <IconChevronRight className="h-4 w-4" />
          </Link>
        ) : (
          <span className={disabledClass} aria-disabled="true">
            Next
            <IconChevronRight className="h-4 w-4" />
          </span>
        )}
      </div>
    </nav>
  );
}
