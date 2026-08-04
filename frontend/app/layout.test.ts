import { metadata } from "./layout";

describe("RootLayout metadata", () => {
  it("exports the standardized favicon and apple-touch metadata", () => {
    expect(metadata).toMatchObject({
      description: "Build your notes library and turn notes into summaries and quizzes.",
      manifest: "/site.webmanifest",
      icons: {
        shortcut: "/favicon.ico",
        icon: expect.arrayContaining([
          expect.objectContaining({ url: "/favicon.ico" }),
          expect.objectContaining({ url: "/favicon-16x16.png", sizes: "16x16" }),
          expect.objectContaining({ url: "/favicon-32x32.png", sizes: "32x32" }),
          expect.objectContaining({ url: "/favicon-192x192.png", sizes: "192x192" }),
          expect.objectContaining({ url: "/favicon-512x512.png", sizes: "512x512" }),
        ]),
        apple: expect.arrayContaining([
          expect.objectContaining({ url: "/apple-touch-icon.png", sizes: "180x180" }),
        ]),
      },
      openGraph: expect.objectContaining({
        images: expect.arrayContaining([
          expect.objectContaining({
            url: "https://notelib.app/og-image-v2.png",
            alt: "NoteLib — your notes become your study system. Turn notes into Study Packs with summaries, key concepts, quizzes, and flashcards.",
          }),
        ]),
      }),
    });
  });
});
