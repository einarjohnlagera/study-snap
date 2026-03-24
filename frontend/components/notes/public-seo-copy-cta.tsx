"use client";

import { useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Button } from "@/components/ui/button";
import { buildLoginPath, getAuthUser } from "@/lib/auth";
import { copyNote, trackAnalyticsEvent } from "@/lib/api";

type PublicSeoCopyCtaProps = {
  noteId: string;
};

export function PublicSeoCopyCta({ noteId }: PublicSeoCopyCtaProps) {
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

    let cancelled = false;
    const runCopy = async () => {
      copyHandledRef.current = true;
      setCopying(true);
      setCopyError(null);
      try {
        const copied = await copyNote(noteId);
        if (!cancelled) {
          router.replace(`/notes/${copied.id}?copied=1`);
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
  }, [noteId, router, searchParams]);

  const handleCopy = async () => {
    if (copying) {
      return;
    }
    void trackAnalyticsEvent({
      eventType: "PUBLIC_NOTE_COPY_CLICKED",
      entityId: noteId,
      metadata: {
        path: pathname,
      },
    });
    if (!getAuthUser()) {
      router.push(buildLoginPath({ redirectTo: `${pathname}?copy=1` }));
      return;
    }

    setCopying(true);
    setCopyError(null);
    try {
      const copied = await copyNote(noteId);
      router.push(`/notes/${copied.id}?copied=1`);
    } catch (error) {
      setCopyError(error instanceof Error ? error.message : "Could not copy note.");
      setCopying(false);
    }
  };

  return (
    <div className="space-y-2">
      <Button type="button" className="w-full sm:w-auto" onClick={() => void handleCopy()} disabled={copying}>
        {copying ? "Preparing your copy..." : "Make a Copy and Generate Your Own Study Pack"}
      </Button>
      {copyError ? <p className="text-xs text-red-600 dark:text-red-400">{copyError}</p> : null}
    </div>
  );
}
