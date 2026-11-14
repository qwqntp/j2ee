import type { TravelerInput } from "./api";

export const GENDERS = ["MALE", "FEMALE", "OTHER"] as const;
export type Gender = (typeof GENDERS)[number];

export type TravelerDraft = {
  fullName: string;
  phone: string;
  email: string;
  dateOfBirth: string;
  gender: Gender | "";
  idDocument: string;
};

export type BookingRequest = {
  departureId: number;
  guestCount: number;
  note?: string;
  travelers: TravelerInput[];
  promotionCode?: string;
};

export function emptyTravelerDraft(): TravelerDraft {
  return {
    fullName: "",
    phone: "",
    email: "",
    dateOfBirth: "",
    gender: "",
    idDocument: "",
  };
}

/** Keep the traveler rows aligned with the guest count without losing typed values. */
export function resizeTravelers(current: TravelerDraft[], count: number): TravelerDraft[] {
  return Array.from({ length: count }, (_, index) => current[index] ?? emptyTravelerDraft());
}

function optional(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
}

export function travelerPayload(draft: TravelerDraft): TravelerInput {
  return {
    fullName: draft.fullName.trim(),
    phone: optional(draft.phone),
    email: optional(draft.email),
    dateOfBirth: optional(draft.dateOfBirth),
    gender: draft.gender || undefined,
    idDocument: optional(draft.idDocument),
  };
}

export function buildBookingRequest(
  departureId: number,
  guestCount: number,
  note: string,
  travelers: TravelerDraft[],
  promotionCode: string,
): BookingRequest {
  return {
    departureId,
    guestCount,
    note: optional(note),
    travelers: travelers.map(travelerPayload),
    promotionCode: optional(promotionCode),
  };
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

/** Client-side mirror of the backend field limits; the API remains authoritative. */
export function validateTravelerDrafts(travelers: TravelerDraft[]): string | null {
  for (const [index, traveler] of travelers.entries()) {
    const label = `Traveler ${index + 1}`;
    const name = traveler.fullName.trim();

    if (!name) return `${label}: full name is required.`;
    if (name.length > 255) return `${label}: full name must be at most 255 characters.`;
    if (traveler.phone.trim().length > 20) {
      return `${label}: phone must be at most 20 characters.`;
    }
    if (traveler.email.trim() && !EMAIL_PATTERN.test(traveler.email.trim())) {
      return `${label}: enter a valid email address.`;
    }
    if (traveler.email.trim().length > 255) {
      return `${label}: email must be at most 255 characters.`;
    }
    if (traveler.dateOfBirth && !ISO_DATE_PATTERN.test(traveler.dateOfBirth)) {
      return `${label}: date of birth must be a valid date.`;
    }
    if (traveler.idDocument.trim().length > 64) {
      return `${label}: ID document must be at most 64 characters.`;
    }
  }
  return null;
}
