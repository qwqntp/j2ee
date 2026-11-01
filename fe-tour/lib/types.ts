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

export type Account = {
  id: number;
  username: string;
  email: string;
  emailVerified: number | null;
  status: number | null;
  roleId: number | null;
  roleName: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type AuthSession = {
  accessToken: string;
  account: Account;
};

export type Booking = {
  id: number;
  bookingCode: string;
  departureId: number | null;
  accountId: number | null;
  createdAt: string;
  guestCount: number;
  unitPrice: number;
  subtotal: number;
  discountAmount: number;
  totalAmount: number;
  status: string;
  note: string | null;
  travelers: Array<{
    id: number;
    name: string;
    dateOfBirth: string | null;
    gender: string | null;
    idDocument: string | null;
  }>;
};
