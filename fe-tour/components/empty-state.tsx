import Link from "next/link";
import { IconCompass } from "./icons";

type Props = {
  title: string;
  description: string;
  action?: { href: string; label: string };
};

export function EmptyState({ title, description, action }: Props) {
  return (
    <div className="rounded-[4px] border border-line bg-white px-6 py-16 text-center">
      <span className="mx-auto grid h-12 w-12 place-items-center rounded-[3px] border border-line text-forest">
        <IconCompass className="h-6 w-6" />
      </span>
      <h3 className="mt-5 font-display text-2xl tracking-tight">{title}</h3>
      <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted">{description}</p>
      {action ? (
        <Link href={action.href} className="btn-secondary mt-6">
          {action.label}
        </Link>
      ) : null}
    </div>
  );
}
