export type PageData<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
  hasNext: boolean;
  hasPrevious: boolean;
};

export type TourSummary = {
  id: number;
  name: string;
  description: string | null;
  status: string;
  imageUrl: string | null;
  priceFrom: number | null;
  nextDepartureDate: string | null;
  seatsAvailable: number;
  departureCount: number;
};

export type TourDeparture = {
  id: number;
  tourId: number;
  startDate: string;
  endDate: string;
  capacity: number;
  bookedSlots: number;
  seatsAvailable: number;
  price: number;
  meetingPoint: string | null;
  status: string;
  createdAt: string;
};

export type TourDetail = {
  id: number;
  name: string;
  description: string | null;
  status: string;
  accommodationName: string | null;
  accommodationRating: number | null;
  accommodationType: string | null;
  guideName: string | null;
  guidePhone: string | null;
  imageUrls: string[];
  itinerary: string[];
  destinationNames: string[];
  vehicleTypes: string[];
  departures: TourDeparture[];
};
