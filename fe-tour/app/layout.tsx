import type { Metadata } from "next";
import { Inter, Playfair_Display } from "next/font/google";
import { FlashToast } from "@/components/flash-toast";
import "./globals.css";

const inter = Inter({
  subsets: ["latin", "vietnamese"],
  variable: "--font-inter",
  display: "swap",
});

const playfair = Playfair_Display({
  subsets: ["latin", "vietnamese"],
  variable: "--font-playfair",
  display: "swap",
  weight: ["400", "500", "600", "700"],
});

export const metadata: Metadata = {
  title: {
    default: "Hành Trình — Vietnam, unhurried",
    template: "%s · Hành Trình",
  },
  description:
    "Small-group journeys across Vietnam, built around local guides, quieter roads and enough time to stay awhile.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${inter.variable} ${playfair.variable} h-full`}>
      <body className="flex min-h-full flex-col bg-ivory text-charcoal antialiased">
        {children}
        <FlashToast />
      </body>
    </html>
  );
}
