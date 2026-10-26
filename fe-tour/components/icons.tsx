import type { ReactNode } from "react";

type IconProps = { className?: string };

function Icon({ className = "h-5 w-5", children }: IconProps & { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

export function IconArrowRight({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="M4 12h16m0 0-6-6m6 6-6 6" />
    </Icon>
  );
}

export function IconSearch({ className }: IconProps) {
  return (
    <Icon className={className}>
      <circle cx="11" cy="11" r="6.5" />
      <path d="m16 16 4.5 4.5" />
    </Icon>
  );
}

export function IconCalendar({ className }: IconProps) {
  return (
    <Icon className={className}>
      <rect x="3.5" y="5" width="17" height="15" rx="2.5" />
      <path d="M8 3.5v3M16 3.5v3M3.5 10h17" />
    </Icon>
  );
}

export function IconUsers({ className }: IconProps) {
  return (
    <Icon className={className}>
      <circle cx="9" cy="8" r="3.25" />
      <path d="M3.5 19.5c.6-3.2 2.7-5 5.5-5s4.9 1.8 5.5 5" />
      <path d="M16 5.2a3 3 0 0 1 0 5.6M17.5 14.9c1.7.6 2.8 2.2 3.1 4.6" />
    </Icon>
  );
}

export function IconMapPin({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="M12 21s6.5-5.4 6.5-10.5a6.5 6.5 0 1 0-13 0C5.5 15.6 12 21 12 21Z" />
      <circle cx="12" cy="10.3" r="2.4" />
    </Icon>
  );
}

export function IconCheck({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="m4.5 12.5 5 5 10-11" />
    </Icon>
  );
}

export function IconStar({ className = "h-4 w-4" }: IconProps) {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" className={className} aria-hidden="true">
      <path d="m12 2.8 2.8 5.9 6.4.9-4.7 4.5 1.2 6.4-5.7-3.1-5.7 3.1 1.2-6.4L2.8 9.6l6.4-.9L12 2.8Z" />
    </svg>
  );
}

export function IconShield({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="M12 3 4.8 5.8v5.4c0 4.3 2.9 7.5 7.2 9.3 4.3-1.8 7.2-5 7.2-9.3V5.8L12 3Z" />
      <path d="m8.7 11.7 2.2 2.2 4.5-4.6" />
    </Icon>
  );
}

export function IconCompass({ className }: IconProps) {
  return (
    <Icon className={className}>
      <circle cx="12" cy="12" r="9" />
      <path d="m15.8 8.2-2.1 5.5-5.5 2.1 2.1-5.5 5.5-2.1Z" />
    </Icon>
  );
}

export function IconSparkles({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="M12 3.5 13.5 8 18 9.5 13.5 11 12 15.5 10.5 11 6 9.5 10.5 8 12 3.5Z" />
      <path d="m18.5 15 .7 2.3 2.3.7-2.3.7-.7 2.3-.7-2.3-2.3-.7 2.3-.7.7-2.3Z" />
    </Icon>
  );
}

export function IconPhone({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="M7.2 3.8 9.5 8 7.7 9.8c1 2.1 2.4 3.5 4.5 4.5l1.8-1.8 4.2 2.3-.7 3.2c-.2.9-1 1.5-1.9 1.4C9.2 18.6 5.4 14.8 4.6 8.4c-.1-.9.5-1.7 1.4-1.9l1.2-.3Z" />
    </Icon>
  );
}

export function IconMail({ className }: IconProps) {
  return (
    <Icon className={className}>
      <rect x="3" y="5" width="18" height="14" rx="2.5" />
      <path d="m4 7 8 6 8-6" />
    </Icon>
  );
}

export function IconClock({ className }: IconProps) {
  return (
    <Icon className={className}>
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </Icon>
  );
}

export function IconChevronLeft({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="m14.5 5-7 7 7 7" />
    </Icon>
  );
}

export function IconChevronRight({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="m9.5 5 7 7-7 7" />
    </Icon>
  );
}

export function IconMenu({ className }: IconProps) {
  return (
    <Icon className={className}>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </Icon>
  );
}
