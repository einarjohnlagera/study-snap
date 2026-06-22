"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { BrandFullLogo } from "@/components/branding/brand-assets";
import { PublicFooter } from "@/components/public/public-footer";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { ApiRequestError, unsubscribeEmail } from "@/lib/api";

function UnsubscribePageContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<"loading" | "success" | "error">("loading");
  const [message, setMessage] = useState("Unsubscribing...");

  useEffect(() => {
    if (!token) {
      setStatus("error");
      setMessage("This unsubscribe link is invalid or has expired.");
      return;
    }

    let cancelled = false;
    setStatus("loading");
    setMessage("Unsubscribing...");

    void unsubscribeEmail(token)
      .then((response) => {
        if (cancelled) {
          return;
        }
        setStatus("success");
        setMessage(response.message || `You've been unsubscribed from ${response.displayName} emails.`);
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }
        setStatus("error");
        if (error instanceof ApiRequestError && error.code === "INVALID_UNSUBSCRIBE_TOKEN") {
          setMessage("This unsubscribe link is invalid or has expired.");
          return;
        }
        setMessage(error instanceof Error ? error.message : "Could not unsubscribe from this email.");
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  const isLoading = status === "loading";
  const title = status === "success"
    ? "You're unsubscribed"
    : status === "error"
      ? "Unsubscribe link issue"
      : "Unsubscribing";

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-6 sm:px-6 sm:py-10">
      <Card className="space-y-6 p-4 sm:p-6">
        <div className="flex justify-center">
          <BrandFullLogo width={192} height={40} priority />
        </div>

        <div className="space-y-2">
          <CardTitle>{title}</CardTitle>
          <CardDescription>{message}</CardDescription>
        </div>

        <div className="flex flex-col gap-3 sm:flex-row">
          <Link
            href="/settings?section=email-preferences"
            aria-disabled={isLoading}
            className={buttonVariants({
              className: isLoading ? "pointer-events-none opacity-50" : undefined,
            })}
          >
            Manage all email preferences
          </Link>
          <Link
            href="/"
            className="inline-flex h-10 items-center justify-center rounded-lg px-4 text-sm font-medium text-foreground/70 underline underline-offset-2 hover:text-foreground"
          >
            Back to NoteLib
          </Link>
        </div>
      </Card>
      <PublicFooter variant="compact" className="mt-6" />
    </div>
  );
}

export default function UnsubscribePage() {
  return (
    <Suspense fallback={null}>
      <UnsubscribePageContent />
    </Suspense>
  );
}
