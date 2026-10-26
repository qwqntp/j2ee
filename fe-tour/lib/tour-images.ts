function normalize(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .toLowerCase();
}

const FALLBACKS = [
  { match: "lan ha", src: "/images/tour-lan-ha.jpg" },
  { match: "ha long", src: "/images/tour-lan-ha.jpg" },
  { match: "cat ba", src: "/images/tour-lan-ha.jpg" },
  { match: "hoi an", src: "/images/tour-hoi-an.jpg" },
  { match: "my son", src: "/images/tour-hoi-an.jpg" },
  { match: "sa pa", src: "/images/tour-sa-pa.jpg" },
  { match: "ninh binh", src: "/images/tour-ninh-binh.jpg" },
  { match: "trang an", src: "/images/tour-ninh-binh.jpg" },
  { match: "tam coc", src: "/images/tour-ninh-binh.jpg" },
  { match: "phu quoc", src: "/images/tour-phu-quoc.jpg" },
  { match: "nha trang", src: "/images/destination-nha-trang.jpg" },
  { match: "hue", src: "/images/tour-hue.jpg" },
];

export function tourFallbackImage(...names: string[]): string | null {
  const haystack = normalize(names.join(" "));
  return FALLBACKS.find((item) => haystack.includes(item.match))?.src ?? null;
}
