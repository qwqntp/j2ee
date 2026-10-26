"use client";

import { useState } from "react";

type Props = {
  src: string | null;
  alt: string;
  fallbackSrc?: string | null;
  className?: string;
  eager?: boolean;
};

export function TourImage({ src, alt, fallbackSrc = null, className, eager = false }: Props) {
  const [failedSrc, setFailedSrc] = useState<string | null>(null);
  const source =
    src && failedSrc !== src
      ? src
      : fallbackSrc && failedSrc !== fallbackSrc
        ? fallbackSrc
        : null;

  if (!source) {
    return (
      <div
        role="img"
        aria-label={alt}
        className={`flex items-center justify-center bg-sage/30 ${className ?? ""}`}
      >
        <span className="font-display text-lg text-forest/50">Hành Trình</span>
      </div>
    );
  }

  return (
    // Remote hosts are configured per deployment, so the raw URL is intentional.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={source}
      alt={alt}
      loading={eager ? "eager" : "lazy"}
      decoding="async"
      onError={() => setFailedSrc(source)}
      className={`object-cover ${className ?? ""}`}
    />
  );
}
