import Link from "next/link";
import { IconArrowRight } from "./icons";

type Props = {
  eyebrow: string;
  title: string;
  description?: string;
  action?: { href: string; label: string };
  className?: string;
};

export function SectionHeading({ eyebrow, title, description, action, className }: Props) {
  return (
    <div
      className={`flex flex-wrap items-end justify-between gap-6 ${className ?? ""}`}
    >
      <div className="max-w-2xl">
        <p className="eyebrow text-forest">{eyebrow}</p>
        <h2 className="mt-3 font-display text-3xl leading-[1.08] tracking-tight text-charcoal sm:text-4xl lg:text-5xl">
          {title}
        </h2>
        {description ? (
          <p className="mt-4 text-base leading-7 text-muted">{description}</p>
        ) : null}
      </div>
      {action ? (
        <Link
          href={action.href}
          className="group inline-flex items-center gap-2 text-sm font-medium text-forest transition-colors hover:text-forest-deep"
        >
          {action.label}
          <IconArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
        </Link>
      ) : null}
    </div>
  );
}
