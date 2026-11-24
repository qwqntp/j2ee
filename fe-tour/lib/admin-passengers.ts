export type Passenger = {
  fullName: string;
  phone: string | null;
  email: string | null;
  dateOfBirth: string | null;
  gender: string | null;
  idDocument: string | null;
  bookingId: number;
  bookingCode: string;
  bookingStatus: string;
  contactNote: string | null;
};

export type PassengerManifest = {
  departureId: number;
  tourName: string;
  startDate: string;
  capacity: number;
  booked: number;
  remaining: number;
  passengers: Passenger[];
};

export function buildPassengerCsv(passengers: Passenger[]): string {
  const headers = ["Full name", "Phone", "Email", "Date of birth", "Gender", "ID document", "Booking code", "Booking status", "Contact note"];
  const escape = (value: string | null) => {
    let text = value ?? "";
    if (/^[\s]*[=+\-@\t\r]/.test(text)) text = `'${text}`;
    return `"${text.replace(/"/g, '""')}"`;
  };
  return [headers, ...passengers.map((p) => [
    p.fullName, p.phone, p.email, p.dateOfBirth, p.gender, p.idDocument,
    p.bookingCode, p.bookingStatus, p.contactNote,
  ])].map((row) => row.map(escape).join(",")).join("\r\n");
}
