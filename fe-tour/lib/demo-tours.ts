import type { PageData, TourDeparture, TourDetail, TourSummary } from "./types";

function dateInDays(days: number, hour = 8): string {
  const date = new Date();
  date.setHours(hour, 0, 0, 0);
  date.setDate(date.getDate() + days);
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(hour)}:00:00`;
}

function departure(
  tourId: number,
  id: number,
  startInDays: number,
  nights: number,
  price: number,
  capacity: number,
  booked: number,
  meetingPoint: string,
  status = "OPEN",
): TourDeparture {
  return {
    id,
    tourId,
    startDate: dateInDays(startInDays),
    endDate: dateInDays(startInDays + nights),
    capacity,
    bookedSlots: booked,
    seatsAvailable: capacity - booked,
    price,
    meetingPoint,
    status,
    createdAt: dateInDays(-45),
  };
}

// Sample journeys only. They are selected outside the cached API reads and
// never stored in the cache as if they were live inventory.
const DEMO_TOURS: TourDetail[] = [
  {
    id: 9001,
    name: "Lan Ha Bay Overnight",
    description:
      "Slip past the day-trippers into the quieter half of the bay, with a night on the water, a sunrise kayak and dinner cooked on deck.",
    status: "OPEN",
    accommodationName: "Yến Sail Boutique Cruise",
    accommodationRating: 4.8,
    accommodationType: "Overnight cruise",
    guideName: "Minh Trần",
    guidePhone: "+84 912 345 601",
    imageUrls: [],
    itinerary: [
      "Day 1 · Hanoi pickup, kayak through Lan Ha's limestone channels, sunset on the top deck.",
      "Day 2 · Sunrise swim, breakfast at a floating village, return to Hanoi by late afternoon.",
    ],
    destinationNames: ["Lan Ha Bay", "Cat Ba Island"],
    vehicleTypes: ["Shuttle bus", "Boat"],
    departures: [
      departure(9001, 900101, 18, 1, 3_450_000, 12, 6, "Hanoi Opera House, 08:00"),
      departure(9001, 900102, 32, 1, 3_850_000, 12, 0, "Hanoi Opera House, 08:00"),
      departure(9001, 900103, 46, 1, 3_450_000, 12, 12, "Hanoi Opera House, 08:00", "FULL"),
    ],
  },
  {
    id: 9002,
    name: "Hoi An Lantern Nights",
    description:
      "Three unhurried days between mustard-yellow lanes, a My Son sunrise and a lantern-lit dinner in a family garden.",
    status: "OPEN",
    accommodationName: "An Nhiên Garden House",
    accommodationRating: 4.7,
    accommodationType: "Boutique hotel",
    guideName: "Linh Nguyễn",
    guidePhone: "+84 912 345 602",
    imageUrls: [],
    itinerary: [
      "Day 1 · Settle into the old town, walk the riverfront and join a lantern-making workshop.",
      "Day 2 · Early transfer to My Son, then a free afternoon by the pool.",
      "Day 3 · Market breakfast, countryside bicycle loop and a farewell dinner.",
    ],
    destinationNames: ["Hoi An", "My Son"],
    vehicleTypes: ["Private car", "Bicycle"],
    departures: [
      departure(9002, 900201, 21, 2, 4_200_000, 10, 4, "Hoi An Central Market, 09:00"),
      departure(9002, 900202, 40, 2, 4_500_000, 10, 9, "Hoi An Central Market, 09:00"),
    ],
  },
  {
    id: 9003,
    name: "Sa Pa Rice Terrace Trek",
    description:
      "Walk between cloud and rice terraces with a Hmong guide, sleep in a mountain lodge and wake up above the valley fog.",
    status: "OPEN",
    accommodationName: "Mountain Mist Lodge",
    accommodationRating: 4.6,
    accommodationType: "Mountain lodge",
    guideName: "A Páo",
    guidePhone: "+84 912 345 603",
    imageUrls: [],
    itinerary: [
      "Day 1 · Night train to Lao Cai, morning transfer to Sa Pa and a gentle valley walk.",
      "Day 2 · Full-day trek to Tả Van, lunch with a local family, lodge check-in.",
      "Day 3 · Sunrise over the terraces, free afternoon in Sa Pa town.",
      "Day 4 · Morning market, return train to Hanoi.",
    ],
    destinationNames: ["Sa Pa", "Tả Van"],
    vehicleTypes: ["Night train", "Trekking"],
    departures: [
      departure(9003, 900301, 26, 3, 5_850_000, 8, 3, "Hanoi Railway Station, 21:00"),
      departure(9003, 900302, 47, 3, 6_200_000, 8, 8, "Hanoi Railway Station, 21:00", "FULL"),
      departure(9003, 900303, 61, 3, 5_850_000, 8, 2, "Hanoi Railway Station, 21:00"),
    ],
  },
  {
    id: 9004,
    name: "Ninh Binh Karst & Rivers",
    description:
      "A slow weekend of sampan rides, cave temples and rice fields, close enough to Hanoi for a two-day escape.",
    status: "OPEN",
    accommodationName: "Tam Cốc Riverside",
    accommodationRating: 4.5,
    accommodationType: "Garden homestay",
    guideName: "Hà Vũ",
    guidePhone: "+84 912 345 604",
    imageUrls: [],
    itinerary: [
      "Day 1 · Cycle to Hang Múa, sunset over the Tam Cốc river, family dinner at the homestay.",
      "Day 2 · Early sampan through Trang An, visit Bái Đính, return to Hanoi.",
    ],
    destinationNames: ["Ninh Binh", "Trang An"],
    vehicleTypes: ["Minivan", "Sampan"],
    departures: [
      departure(9004, 900401, 12, 1, 2_950_000, 14, 8, "Hanoi Old Quarter, 07:30"),
      departure(9004, 900402, 33, 1, 3_150_000, 14, 14, "Hanoi Old Quarter, 07:30", "FULL"),
    ],
  },
  {
    id: 9005,
    name: "Phu Quoc Slow Coast",
    description:
      "Five barefoot days on the island's quieter west coast: small boats, night markets and long lunches by the water.",
    status: "OPEN",
    accommodationName: "Mango Bay Hideaway",
    accommodationRating: 4.7,
    accommodationType: "Beach bungalow",
    guideName: "Duy Phạm",
    guidePhone: "+84 912 345 605",
    imageUrls: [],
    itinerary: [
      "Day 1 · Airport pickup, settle into a garden bungalow, sunset swim.",
      "Day 2 · Boat trip to the southern islets, snorkelling and a beach lunch.",
      "Day 3 · Free day, optional pepper farm and fish sauce house visit.",
      "Day 4 · Sunrise kayak, afternoon at the night market.",
      "Day 5 · Slow breakfast and transfer to the airport.",
    ],
    destinationNames: ["Phu Quoc"],
    vehicleTypes: ["Flight", "Boat"],
    departures: [
      departure(9005, 900501, 30, 4, 8_900_000, 10, 5, "Phu Quoc International Airport, 11:00"),
      departure(9005, 900502, 54, 4, 9_400_000, 10, 2, "Phu Quoc International Airport, 11:00"),
    ],
  },
  {
    id: 9006,
    name: "Hue Imperial Streets",
    description:
      "Royal tombs, river ferries and the best bún bò in the country, told through the people who still live inside the old capital.",
    status: "OPEN",
    accommodationName: "Perfume River House",
    accommodationRating: 4.6,
    accommodationType: "Heritage hotel",
    guideName: "Thảo Lê",
    guidePhone: "+84 912 345 606",
    imageUrls: [],
    itinerary: [
      "Day 1 · Cyclo through the citadel, royal tomb visit and an evening river cruise.",
      "Day 2 · Food walk with a local family, afternoon at leisure.",
      "Day 3 · Early market visit and train back to Da Nang or Hanoi.",
    ],
    destinationNames: ["Hue", "Perfume River"],
    vehicleTypes: ["Train", "Cyclo"],
    departures: [
      departure(9006, 900601, 16, 2, 3_750_000, 12, 7, "Hue Railway Station, 08:30"),
      departure(9006, 900602, 38, 2, 4_050_000, 12, 11, "Hue Railway Station, 08:30"),
    ],
  },
];

function toSummary(tour: TourDetail): TourSummary {
  const bookable = tour.departures.filter(
    (item) => item.status === "OPEN" || item.status === "FULL",
  );
  const next = [...bookable].sort((a, b) => a.startDate.localeCompare(b.startDate))[0];

  return {
    id: tour.id,
    name: tour.name,
    description: tour.description,
    status: tour.status,
    imageUrl: tour.imageUrls[0] ?? null,
    priceFrom: bookable.length ? Math.min(...bookable.map((item) => item.price)) : null,
    nextDepartureDate: next?.startDate ?? null,
    seatsAvailable: bookable
      .filter((item) => item.status === "OPEN")
      .reduce((total, item) => total + item.seatsAvailable, 0),
    departureCount: bookable.length,
  };
}

export function demoFeaturedTours(limit = 6): TourSummary[] {
  return DEMO_TOURS.slice(0, limit).map(toSummary);
}

export function demoToursPage(page: number, keyword: string): PageData<TourSummary> {
  const search = keyword.trim().toLowerCase();
  const matches = search
    ? DEMO_TOURS.filter(
        (tour) =>
          tour.name.toLowerCase().includes(search) ||
          (tour.description ?? "").toLowerCase().includes(search) ||
          tour.destinationNames.some((name) => name.toLowerCase().includes(search)),
      )
    : DEMO_TOURS;

  const size = 9;
  const totalPages = matches.length === 0 ? 0 : Math.ceil(matches.length / size);
  const content = matches.slice(page * size, page * size + size).map(toSummary);

  return {
    content,
    page,
    size,
    totalElements: matches.length,
    totalPages,
    last: totalPages === 0 || page >= totalPages - 1,
    hasNext: page < totalPages - 1,
    hasPrevious: page > 0,
  };
}

export function demoTour(id: number): TourDetail | null {
  return DEMO_TOURS.find((tour) => tour.id === id) ?? null;
}
