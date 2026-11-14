export type ProfileFormValues = {
  name: string;
  email: string;
  phone: string;
  address: string;
  dateOfBirth: string;
};

export type ProfilePayload = {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  dateOfBirth?: string;
};

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function optional(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed === "" ? undefined : trimmed;
}

export function profilePayload(values: ProfileFormValues): ProfilePayload {
  return {
    name: values.name.trim(),
    email: optional(values.email),
    phone: optional(values.phone),
    address: optional(values.address),
    dateOfBirth: optional(values.dateOfBirth),
  };
}

function todayIso(now: Date): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function validIsoDate(value: string): boolean {
  if (!ISO_DATE_PATTERN.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  );
}

/** Mirrors the backend profile constraints; the API remains authoritative. */
export function validateProfile(values: ProfileFormValues, now: Date = new Date()): string | null {
  const name = values.name.trim();
  if (!name) return "Name is required.";
  if (name.length > 255) return "Name must be at most 255 characters.";

  const email = values.email.trim();
  if (email) {
    if (!EMAIL_PATTERN.test(email)) return "Email must be valid.";
    if (email.length > 255) return "Email must be at most 255 characters.";
  }

  if (values.phone.trim().length > 20) return "Phone must be at most 20 characters.";
  if (values.address.trim().length > 255) return "Address must be at most 255 characters.";

  if (values.dateOfBirth) {
    if (!validIsoDate(values.dateOfBirth)) return "Date of birth must be a valid date.";
    if (values.dateOfBirth > todayIso(now)) {
      return "Date of birth must not be in the future.";
    }
  }

  return null;
}

/** Mirrors the register/change-password rules, including the BCrypt 72-byte limit. */
export function validatePasswordChange(
  currentPassword: string,
  newPassword: string,
  confirmation: string,
): string | null {
  if (!currentPassword) return "Current password is required.";
  if (!newPassword) return "New password is required.";
  if (newPassword.length < 8 || newPassword.length > 100) {
    return "Password must be between 8 and 100 characters.";
  }
  if (new TextEncoder().encode(newPassword).length > 72) {
    return "Password must be at most 72 bytes.";
  }
  if (newPassword !== confirmation) return "New passwords do not match.";
  if (newPassword === currentPassword) {
    return "New password must be different from the current password.";
  }
  return null;
}
