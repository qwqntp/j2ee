import Link from "next/link";
import { IconMail, IconMapPin, IconPhone } from "./icons";

export function SiteFooter() {
  return (
    <footer id="contact" className="mt-20 bg-forest text-ivory">
      <div className="shell grid gap-10 py-14 md:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_1.2fr]">
        <div>
          <p className="font-display text-3xl tracking-tight">Hành Trình</p>
          <p className="mt-1 text-[10px] font-medium uppercase tracking-[0.22em] text-sage">
            Vietnam travel atelier
          </p>
          <p className="mt-5 max-w-sm text-sm leading-6 text-ivory/70">
            Small-group journeys across Vietnam, built around local guides, quieter roads and
            enough time to stay awhile.
          </p>
        </div>

        <div>
          <p className="eyebrow text-sage">Explore</p>
          <ul className="mt-4 space-y-3 text-sm text-ivory/70">
            <li>
              <Link href="/tours" className="transition-colors hover:text-white">
                All journeys
              </Link>
            </li>
            <li>
              <Link href="/#destinations" className="transition-colors hover:text-white">
                Destinations
              </Link>
            </li>
            <li>
              <Link href="/#stories" className="transition-colors hover:text-white">
                Travel notes
              </Link>
            </li>
            <li>
              <Link href="/#why" className="transition-colors hover:text-white">
                Why travel with us
              </Link>
            </li>
          </ul>
        </div>

        <div>
          <p className="eyebrow text-sage">Practical</p>
          <ul className="mt-4 space-y-3 text-sm text-ivory/70">
            <li>
              <Link href="/payment" className="transition-colors hover:text-white">
                Checkout preview
              </Link>
            </li>
            <li>
              <a href="/images/CREDITS.md" className="transition-colors hover:text-white">
                Photography credits
              </a>
            </li>
            <li>
              <a href="mailto:hello@hanhtrinh.example" className="transition-colors hover:text-white">
                hello@hanhtrinh.example
              </a>
            </li>
          </ul>
        </div>

        <div>
          <p className="eyebrow text-sage">Talk to us</p>
          <ul className="mt-4 space-y-3 text-sm text-ivory/70">
            <li className="flex items-center gap-2.5">
              <IconMail className="h-4 w-4 text-sage" />
              <a href="mailto:hello@hanhtrinh.example" className="transition-colors hover:text-white">
                hello@hanhtrinh.example
              </a>
            </li>
            <li className="flex items-center gap-2.5">
              <IconPhone className="h-4 w-4 text-sage" />
              <span>+84 28 0000 0000</span>
            </li>
            <li className="flex items-center gap-2.5">
              <IconMapPin className="h-4 w-4 text-sage" />
              <span>Ho Chi Minh City, Vietnam</span>
            </li>
          </ul>
        </div>
      </div>

      <div className="border-t border-white/15">
        <div className="shell flex flex-wrap items-center justify-between gap-3 py-6 text-xs text-ivory/50">
          <p>© 2026 Hành Trình. A demo booking experience.</p>
          <p>
            Photography by Christophe95, Steffen Schmitz and others via{" "}
            <a href="/images/CREDITS.md" className="underline underline-offset-2 hover:text-white">
              Wikimedia Commons
            </a>
            .
          </p>
        </div>
      </div>
    </footer>
  );
}
