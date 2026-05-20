import type { Metadata } from "next";
import { Suspense } from "react";
import localFont from "next/font/local";
import "./globals.css";
import { ThemePreferenceSync } from "@/components/theme-preference-sync";
import { ThemeProvider } from "@/components/theme-provider";
import { AppShell } from "@/components/app-shell";
import { AppShellTitleProvider } from "@/components/app-shell-title-context";
import { ExamFocusProvider } from "@/components/exam-mode/exam-focus-context";
import { RouteProgressProvider } from "@/components/navigation/route-progress-provider";
import { DEFAULT_OG_IMAGE_ALT, DEFAULT_OG_IMAGE_URL, SITE_NAME, SITE_URL } from "@/lib/site-metadata";
import { THEME_CLASS_NAME_BY_MODE } from "@/lib/theme-preferences";

const geistSans = localFont({
  src: "../node_modules/next/dist/next-devtools/server/font/geist-latin.woff2",
  variable: "--font-geist-sans",
  weight: "100 900",
  display: "swap",
});

const geistMono = localFont({
  src: "../node_modules/next/dist/next-devtools/server/font/geist-mono-latin.woff2",
  variable: "--font-geist-mono",
  weight: "100 900",
  display: "swap",
});

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: "NoteLib",
  description: "Build your notes library and turn notes into summaries and quizzes.",
  manifest: "/site.webmanifest",
  icons: {
    icon: [
      { url: "/favicon.ico" },
      { url: "/favicon-16x16.png", type: "image/png", sizes: "16x16" },
      { url: "/favicon-32x32.png", type: "image/png", sizes: "32x32" },
      { url: "/favicon-192x192.png", type: "image/png", sizes: "192x192" },
      { url: "/favicon-512x512.png", type: "image/png", sizes: "512x512" },
    ],
    shortcut: "/favicon.ico",
    apple: [
      { url: "/apple-touch-icon.png", sizes: "180x180" },
    ],
  },
  openGraph: {
    siteName: SITE_NAME,
    images: [
      {
        url: DEFAULT_OG_IMAGE_URL,
        width: 1200,
        height: 630,
        alt: DEFAULT_OG_IMAGE_ALT,
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    images: [DEFAULT_OG_IMAGE_URL],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} min-h-screen antialiased`}
      >
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          enableColorScheme
          themes={["light", "dark"]}
          value={THEME_CLASS_NAME_BY_MODE}
        >
          <ThemePreferenceSync />
          <Suspense fallback={null}>
            <RouteProgressProvider>
              <ExamFocusProvider>
                <AppShellTitleProvider>
                  <AppShell>{children}</AppShell>
                </AppShellTitleProvider>
              </ExamFocusProvider>
            </RouteProgressProvider>
          </Suspense>
        </ThemeProvider>
      </body>
    </html>
  );
}
