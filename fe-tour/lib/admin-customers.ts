export type AdminCustomer = {
  id: number; name: string; email: string | null; phone: string | null;
  address: string | null; dateOfBirth: string | null; accountId: number | null;
};
export type AdminCustomerBooking = { id: number; code: string; tourName: string; status: string; total: number };
