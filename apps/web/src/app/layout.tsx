import type { Metadata } from "next";
import type { ReactNode } from "react";
import "@fontsource/vazirmatn/400.css";
import "@fontsource/vazirmatn/500.css";
import "@fontsource/vazirmatn/700.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "PrivatePay | فضای آزمایشی",
  description: "محیط آزمایشی PrivatePay با داده‌های ساختگی و پرداخت شبیه‌سازی‌شده.",
  manifest: "/manifest.webmanifest",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: ReactNode;
}>) {
  return (
    <html lang="fa" dir="rtl">
      <body>{children}</body>
    </html>
  );
}
