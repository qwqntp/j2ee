import { AdminCustomers } from "@/components/admin-customers";
import { Suspense } from "react";
export default function CustomersPage() { return <Suspense fallback={null}><AdminCustomers /></Suspense>; }
