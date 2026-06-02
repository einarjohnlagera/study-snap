import { ImageResponse } from "next/og";
import { getServerPublicNoteBySeoPath } from "@/lib/server-public-notes";
import { normalizePublicNoteText } from "@/lib/public-note-text";

export const alt = "Study note on NoteLib — quiz yourself in seconds";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

const MAX_TITLE_LENGTH = 90;

type OpengraphImageProps = {
  params: Promise<{ subject: string; slug: string }>;
};

export default async function OpengraphImage({ params }: OpengraphImageProps) {
  const { subject, slug } = await params;
  const note = await getServerPublicNoteBySeoPath(subject, slug).catch(() => null);

  const rawTitle = normalizePublicNoteText(note?.title?.trim()) || "Study Note";
  const title = rawTitle.length > MAX_TITLE_LENGTH
    ? `${rawTitle.slice(0, MAX_TITLE_LENGTH - 1).trimEnd()}…`
    : rawTitle;
  const subjectLabel = normalizePublicNoteText(note?.subject?.trim()) || "Study Notes";
  const questionCount = note?.quiz?.length ?? 0;
  const footerLine = questionCount > 0
    ? `${questionCount} practice question${questionCount === 1 ? "" : "s"} · Quiz yourself in seconds`
    : "Summaries, key concepts, and practice questions";

  return new ImageResponse(
    (
      <div
        style={{
          height: "100%",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          padding: "72px",
          color: "#ffffff",
          background: "linear-gradient(135deg, #1e3a8a 0%, #2563eb 55%, #3b82f6 100%)",
          fontFamily: "sans-serif",
        }}
      >
        <div style={{ display: "flex", alignItems: "center" }}>
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: "64px",
              height: "64px",
              borderRadius: "18px",
              background: "#ffffff",
              color: "#2563eb",
              fontSize: "34px",
              fontWeight: 800,
              marginRight: "20px",
            }}
          >
            NL
          </div>
          <div style={{ display: "flex", fontSize: "36px", fontWeight: 700 }}>NoteLib</div>
        </div>

        <div style={{ display: "flex", flexDirection: "column" }}>
          <div style={{ display: "flex", marginBottom: "24px" }}>
            <div
              style={{
                display: "flex",
                padding: "10px 24px",
                borderRadius: "999px",
                background: "rgba(255, 255, 255, 0.18)",
                fontSize: "30px",
                fontWeight: 600,
              }}
            >
              {subjectLabel}
            </div>
          </div>
          <div style={{ display: "flex", fontSize: "68px", fontWeight: 800, lineHeight: 1.08 }}>
            {title}
          </div>
        </div>

        <div style={{ display: "flex", fontSize: "36px", fontWeight: 600, color: "rgba(255, 255, 255, 0.95)" }}>
          {footerLine}
        </div>
      </div>
    ),
    { ...size },
  );
}
