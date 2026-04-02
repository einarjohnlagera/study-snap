"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { ResponsiveActionButton } from "@/components/ui/action-button";
import { buildLoginPath, getAuthUser } from "@/lib/auth";
import { copyNote, trackAnalyticsEvent } from "@/lib/api";

type PublicSeoCopyCtaProps = {
  noteId: string;
  label?: string;
  redirectTarget?: "library" | "generate";
};

function buildCopiedNotePath(noteId: string, redirectTarget: "library" | "generate") {
  const next = new URLSearchParams({ copied: "1" });
  if (redirectTarget === "generate") {
    next.set("generate", "1");
  }
  return `/notes/${noteId}?${next.toString()}`;
}

export function PublicSeoCopyCta({
  noteId,
  label = "Make a Copy and Generate Your Own Study Pack",
  redirectTarget = "library",
}: Readonly<PublicSeoCopyCtaProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [copying, setCopying] = useState(false);
  const [copyError, setCopyError] = useState<string | null>(null);
  const copyHandledRef = useRef(false);

  useEffect(() => {
    const shouldAutoCopy = searchParams.get("copy") === "1";
    if (!shouldAutoCopy || copyHandledRef.current || !getAuthUser()) {
      return;
    }
    const requestedRedirectTarget = searchParams.get("intent") === "generate" ? "generate" : redirectTarget;

    let cancelled = false;
    const runCopy = async () => {
      copyHandledRef.current = true;
      setCopying(true);
      setCopyError(null);
      try {
        const copied = await copyNote(noteId);
        if (!cancelled) {
          router.replace(buildCopiedNotePath(copied.id, requestedRedirectTarget));
        }
      } catch (error) {
        if (!cancelled) {
          setCopyError(error instanceof Error ? error.message : "Could not copy note.");
          setCopying(false);
        }
      }
    };

    void runCopy();
    return () => {
      cancelled = true;
    };
  }, [noteId, redirectTarget, router, searchParams]);

  const handleCopy = async () => {
    if (copying) {
      return;
    }
    void trackAnalyticsEvent({
      eventType: "PUBLIC_NOTE_COPY_CLICKED",
      entityId: noteId,
      metadata: {
        path: pathname,
        redirectTarget,
      },
    });
    if (!getAuthUser()) {
      router.push(buildLoginPath({ redirectTo: `${pathname}?copy=1&intent=${redirectTarget}` }));
      return;
    }

    setCopying(true);
    setCopyError(null);
    try {
      const copied = await copyNote(noteId);
      router.push(buildCopiedNotePath(copied.id, redirectTarget));
    } catch (error) {
      setCopyError(error instanceof Error ? error.message : "Could not copy note.");
      setCopying(false);
    }
  };

  return (
    <div className="space-y-2">
      <ResponsiveActionButton
        type="button"
        className="w-full sm:w-auto"
        onClick={() => void handleCopy()}
        disabled={copying}
        action="copy"
        label={copying ? "Preparing your copy..." : label}
        showTextOnMobile
      />
      {copyError ? <p className="text-xs text-red-600 dark:text-red-400">{copyError}</p> : null}
    </div>
  );
}
