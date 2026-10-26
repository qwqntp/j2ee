type Props = {
  src: string;
  alt: string;
  className?: string;
  eager?: boolean;
};

export function Photo({ src, alt, className, eager = false }: Props) {
  return (
    // Local editorial images are pre-sized; remote tour images use TourImage.
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={src}
      alt={alt}
      loading={eager ? "eager" : "lazy"}
      decoding="async"
      className={`object-cover ${className ?? ""}`}
    />
  );
}
