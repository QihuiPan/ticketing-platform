import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "SeatForge | Concurrency-safe ticketing",
  description: "A production-oriented ticket reservation demo that prevents overselling.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
