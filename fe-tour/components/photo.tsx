import Image from "next/image";

type Props = {
  src: string;
  alt: string;
  className?: string;
  eager?: boolean;
};

export function Photo({ src, alt, className, eager = false }: Props) {
  return (
    <Image
      src={src}
      alt={alt}
      fill
      sizes="100vw"
      preload={eager}
      className={`object-cover ${className ?? ""}`}
    />
  );
}
