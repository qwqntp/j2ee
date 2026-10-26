"use client";

import Link from "next/link";
import { useRef } from "react";
import { IconArrowRight, IconMenu } from "./icons";

const links = [
  { href: "/tours", label: "Journeys" },
  { href: "/#destinations", label: "Destinations" },
  { href: "/#stories", label: "Travel notes" },
  { href: "/#why", label: "Why us" },
];

export function SiteHeader() {
  const menu = useRef<HTMLDetailsElement>(null);

  return (
    <header className="sticky top-0 z-40 border-b border-line bg-ivory">
      <div className="shell flex h-20 items-center justify-between gap-6">
        <Link href="/" className="group flex items-baseline gap-3" aria-label="Hành Trình home">
          <span className="font-display text-[1.65rem] leading-none tracking-tight text-forest">
            Hành Trình
          </span>
          <span className="hidden border-l border-line pl-3 text-[10px] font-medium uppercase tracking-[0.22em] text-muted sm:block">
            Vietnam travel atelier
          </span>
        </Link>

        <nav className="hidden items-center gap-7 text-sm md:flex" aria-label="Main">
          {links.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-muted transition-colors hover:text-forest"
            >
              {link.label}
            </Link>
          ))}
        </nav>

        <Link href="/tours" className="btn-primary hidden md:inline-flex">
          Find a journey
          <IconArrowRight className="h-4 w-4" />
        </Link>

        <details ref={menu} className="relative md:hidden">
          <summary className="grid h-11 w-11 cursor-pointer list-none place-items-center rounded-[3px] border border-line text-charcoal transition-colors hover:border-forest hover:text-forest [&::-webkit-details-marker]:hidden">
            <IconMenu />
            <span className="sr-only">Open menu</span>
          </summary>
          <div className="absolute right-0 top-14 w-64 rounded-[3px] border border-line bg-ivory p-2 shadow-panel">
            <nav
              aria-label="Mobile"
              className="flex flex-col"
              onClick={() => menu.current?.removeAttribute("open")}
            >
              {links.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className="rounded-[2px] px-4 py-3 text-sm font-medium text-muted transition-colors hover:bg-sage/20 hover:text-forest"
                >
                  {link.label}
                </Link>
              ))}
              <Link href="/tours" className="btn-primary mt-2">
                Find a journey
                <IconArrowRight className="h-4 w-4" />
              </Link>
            </nav>
          </div>
        </details>
      </div>
    </header>
  );
}
