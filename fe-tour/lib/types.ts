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

/** Allowlisted server sorts; the backend appends a stable ID tie-breaker. */
export type AdminListSort =
  | "createdAt,asc"
  | "createdAt,desc"
  | "totalAmount,asc"
  | "totalAmount,desc";

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

export type TourDestinationOption = {
  id: number;
  name: string;
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
  email: string | null;
  emailVerified: number | null;
  status: number | null;
  roleId: number | null;
  roleName: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type AdminAccount = {
  id: number;
  username: string;
  email: string | null;
  emailVerified: number | null;
  status: number | null;
  roleId: number | null;
  roleName: string | null;
  createdAt?: string;
  updatedAt?: string;
};

export type AccountRoleResponse = {
  id: number;
  roleName: string;
};

export type AccountUpdateInput = {
  username: string;
  password?: string;
  email: string | null;
  emailVerified: number;
  status: number;
  roleId: number;
};

export type Profile = {
  customerId: number | null;
  username: string;
  email: string | null;
  emailVerified: number | null;
  name: string | null;
  phone: string | null;
  address: string | null;
  dateOfBirth: string | null;
  passwordSet: boolean;
  googleLinked: boolean;
};

export type AuthSession = {
  accessToken: string;
  account: Account;
};

export type BookingTour = {
  tourId: number;
  tourName: string;
  imageUrl: string | null;
  startDate: string;
  endDate: string;
  meetingPoint: string | null;
  departureStatus: string;
};

export type BookingPromotion = {
  id: number;
  code: string;
  name: string;
  discountPercentage: number;
};

export type Promotion = {
  id: number;
  name: string;
  discountPercentage: number;
  startDate: string;
  endDate: string;
  description: string | null;
  code: string | null;
  active: boolean | null;
  usageLimit: number | null;
  usedCount: number;
  minOrderAmount: number | null;
};

export type PromotionInput = Omit<Promotion, "id" | "usedCount">;

export type Payment = {
  id: number;
  bookingId: number;
  amount: number;
  paymentMethod: string;
  providerTxnId: string | null;
  status: string;
  paidAt: string | null;
  createdAt: string;
};

export type VnpayStart = {
  reference: string;
  paymentUrl: string;
  expiresAt: string;
};

export type AccountCreateInput = AccountUpdateInput & { password: string };

export type VnpayStatus = {
  bookingId: number;
  reference: string;
  status: "PENDING" | "SUCCESS" | "FAILED";
  expired: boolean;
  returnOutcome: "SUCCESS" | "FAILED" | "CANCELLED" | "EXPIRED" | "UNRESOLVED" | null;
  reason: string | null;
  providerCode: string | null;
  providerStatus: string | null;
};

export type VnpayReconcile = {
  bookingId: number;
  reference: string | null;
  status: "PENDING" | "SUCCESS" | "FAILED" | null;
  checked: boolean;
  reason: string | null;
  providerCode: string | null;
  providerStatus: string | null;
};

export type Invoice = {
  id: number;
  bookingId: number;
  createdAt: string;
  status: string;
  discountAmount: number;
  taxAmount: number;
  serviceFee: number;
  totalAmount: number;
  paymentMethod: string | null;
  customerName: string | null;
  customerPhone: string | null;
  customerEmail: string | null;
  promotionIds: number[];
};

/** Scalar row returned by the admin invoice list. */
export type AdminInvoiceListItem = {
  id: number;
  bookingId: number;
  createdAt: string;
  status: string;
  totalAmount: number;
  paymentMethod: string | null;
  customerName: string | null;
  customerEmail: string | null;
};

/** Scalar row returned by the admin booking list. */
export type AdminBookingListItem = {
  id: number;
  bookingCode: string;
  tourName: string | null;
  startDate: string | null;
  endDate: string | null;
  guestCount: number;
  totalAmount: number;
  status: string;
};

export type AdminTour = TourDetail & {
  durationDays: number | null;
  accommodationId: number | null;
  tourGuideId: number | null;
  promotionId: number | null;
  destinationIds: number[];
  vehicleIds: number[];
};

export type TourInput = {
  name: string;
  description?: string;
  durationDays?: number;
  status: string;
  imageUrls: string[];
  accommodationId?: number;
  tourGuideId?: number;
  promotionId?: number;
  destinationIds?: number[];
  vehicleIds?: number[];
};

export type DepartureInput = {
  tourId: number;
  startDate: string;
  endDate: string;
  capacity: number;
  price: number;
  meetingPoint?: string;
  status?: string;
};

export type Booking = {
  id: number;
  bookingCode: string;
  departureId: number | null;
  tour: BookingTour | null;
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
    phone: string | null;
    email: string | null;
  }>;
  promotion: BookingPromotion | null;
};

export type AdminStats = {
  generatedAt: string;
  totalTours: number;
  openTours: number;
  bookingsByStatus: Record<"PENDING" | "PAID" | "CONFIRMED" | "COMPLETED" | "CANCELLED", number>;
  successfulRevenue: number;
  successfulPayments: number;
  averageSuccessfulPayment: number;
  upcomingDepartures: number;
  lowCapacityDepartures: number;
  soldOutDepartures: number;
  topTours: Array<{ tourId: number; tourName: string; bookingCount: number; revenue: number }>;
  capacityRisks: Array<{
    departureId: number;
    tourId: number;
    tourName: string;
    startDate: string;
    capacity: number;
    remainingSeats: number;
    status: string;
  }>;
};

export type CatalogResource = "destinations" | "accommodations" | "vehicles" | "guides" | "images";

export type DestinationRow = {
  id: number;
  name: string;
  location: string | null;
  description: string | null;
  entryFee: number | null;
};

export type AccommodationRow = {
  id: number;
  name: string;
  location: string;
  rating: number | null;
  pricePerNight: number | null;
  type: "HOTEL" | "RESORT" | "HOMESTAY" | null;
};

export type VehicleRow = {
  id: number;
  type: string;
  capacity: number;
  rentalPricePerDay: number | null;
};

export type GuideRow = {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  experienceYears: number;
};

export type ImageRow = {
  id: number;
  imageUrl: string;
  tourId: number | null;
};

export type CatalogRow = DestinationRow | AccommodationRow | VehicleRow | GuideRow | ImageRow;

export type DestinationInput = {
  name: string;
  location?: string;
  description?: string;
  entryFee?: number;
};

export type AccommodationInput = {
  name: string;
  location: string;
  rating?: number;
  pricePerNight?: number;
  type?: "HOTEL" | "RESORT" | "HOMESTAY";
};

export type VehicleInput = {
  type: string;
  capacity: number;
  rentalPricePerDay: number;
};

export type GuideInput = {
  name: string;
  email?: string;
  phone?: string;
  experienceYears: number;
};

export type ImageInput = {
  imageUrl: string;
  tourId: number;
};

export type CatalogInputByResource = {
  destinations: DestinationInput;
  accommodations: AccommodationInput;
  vehicles: VehicleInput;
  guides: GuideInput;
  images: ImageInput;
};

export type CatalogInput = CatalogInputByResource[CatalogResource];

export type CatalogInputFor<R extends CatalogResource> = CatalogInputByResource[R];
