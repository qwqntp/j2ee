export type EditorialDestination = {
  name: string;
  region: string;
  description: string;
  image: string;
  alt: string;
};

export type TravelNote = {
  category: string;
  title: string;
  excerpt: string;
  image: string;
  alt: string;
};

export type TrustReason = {
  number: string;
  title: string;
  copy: string;
};

export const destinations: EditorialDestination[] = [
  {
    name: "Hội An",
    region: "Quảng Nam",
    description:
      "Lantern-lit lanes, tailors and a river that slows everything down.",
    image: "/images/destination-hoi-an.jpg",
    alt: "Mustard-yellow shophouses and lanterns in Hội An's old town",
  },
  {
    name: "Nha Trang",
    region: "Khánh Hòa",
    description: "A long city beach with quiet islands a short boat ride offshore.",
    image: "/images/destination-nha-trang.jpg",
    alt: "A green island in the turquoise water of Nha Trang Bay",
  },
  {
    name: "Lan Ha Bay",
    region: "Hải Phòng",
    description:
      "The quieter, limestone half of the bay — best seen from the water.",
    image: "/images/destination-lan-ha.jpg",
    alt: "Limestone karsts rising from the green water of Lan Ha Bay",
  },
  {
    name: "Sa Pa",
    region: "Lào Cai",
    description:
      "Rice terraces, mountain air and paths that reward an early start.",
    image: "/images/destination-sa-pa.jpg",
    alt: "Terraced rice fields stepping down a Sa Pa valley",
  },
];

export const travelNotes: TravelNote[] = [
  {
    category: "Craft",
    title: "The lantern makers of Hội An",
    excerpt:
      "Inside a family workshop where bamboo, silk and patience turn into light.",
    image: "/images/story-lanterns.jpg",
    alt: "Craftspeople working among hundreds of glowing lanterns in a Hội An workshop",
  },
  {
    category: "Food",
    title: "A bowl of phở, and the morning it belongs to",
    excerpt:
      "Why the best breakfast in Vietnam is also the simplest one.",
    image: "/images/story-pho.jpg",
    alt: "A bowl of phở with beef and fresh herbs",
  },
  {
    category: "Coast",
    title: "The art of doing less by the sea",
    excerpt:
      "A slow morning under the palms in Nha Trang, with nowhere else to be.",
    image: "/images/story-coast.jpg",
    alt: "Palm trees framing the sand and umbrellas on Nha Trang beach",
  },
];

export const trustReasons: TrustReason[] = [
  {
    number: "01",
    title: "Small by design",
    copy: "Groups stay small enough for real conversations and quiet places.",
  },
  {
    number: "02",
    title: "Local, not scripted",
    copy: "Guides who grew up around the route, and the freedom to follow a good detour.",
  },
  {
    number: "03",
    title: "Clear from the start",
    copy: "One price per traveler, dates you can see, and no surprises added at checkout.",
  },
  {
    number: "04",
    title: "Looked after",
    copy: "One team from first question to final transfer, with someone reachable along the way.",
  },
];
