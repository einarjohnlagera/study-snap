"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: {
          initialize: (options: {
            client_id: string;
            callback: (response: { credential?: string }) => void;
          }) => void;
          renderButton: (
            parent: HTMLElement,
            options: {
              theme: "outline";
              size: "large";
              width?: string;
              text?: "continue_with" | "signin_with" | "signup_with";
            },
          ) => void;
        };
      };
    };
  }
}

type GoogleAuthButtonProps = {
  label: string;
  loadingLabel: string;
  disabled?: boolean;
  onCredential: (credential: string) => void | Promise<void>;
  onError: (message: string) => void;
};

const GOOGLE_SCRIPT_ID = "google-identity-services";
const GOOGLE_SCRIPT_SRC = "https://accounts.google.com/gsi/client";

function ensureGoogleScript(): Promise<void> {
  if (globalThis.window === undefined) {
    return Promise.reject(new Error("Google login is only available in the browser."));
  }
  if (globalThis.window.google?.accounts?.id) {
    return Promise.resolve();
  }
  const existing = globalThis.document.getElementById(GOOGLE_SCRIPT_ID) as HTMLScriptElement | null;
  if (existing) {
    return new Promise((resolve, reject) => {
      existing.addEventListener("load", () => resolve(), { once: true });
      existing.addEventListener("error", () => reject(new Error("Could not load Google login.")), { once: true });
    });
  }
  return new Promise((resolve, reject) => {
    const script = globalThis.document.createElement("script");
    script.id = GOOGLE_SCRIPT_ID;
    script.src = GOOGLE_SCRIPT_SRC;
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error("Could not load Google login."));
    globalThis.document.head.appendChild(script);
  });
}

export function GoogleAuthButton({
  label,
  loadingLabel,
  disabled = false,
  onCredential,
  onError,
}: Readonly<GoogleAuthButtonProps>) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [rendered, setRendered] = useState(false);
  const [authenticating, setAuthenticating] = useState(false);
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

  useEffect(() => {
    if (!clientId || disabled || !containerRef.current) {
      return;
    }
    let cancelled = false;
    void ensureGoogleScript()
      .then(() => {
        if (cancelled || !containerRef.current || !globalThis.window.google?.accounts?.id) {
          return;
        }
        containerRef.current.innerHTML = "";
        globalThis.window.google.accounts.id.initialize({
          client_id: clientId,
          callback: (response) => {
            if (!response.credential) {
              onError("Google did not return a credential. Please try again.");
              return;
            }
            setAuthenticating(true);
            void onCredential(response.credential);
          },
        });
        globalThis.window.google.accounts.id.renderButton(containerRef.current, {
          theme: "outline",
          size: "large",
          width: "320",
          text: "continue_with",
        });
        setRendered(true);
      })
      .catch((error: unknown) => {
        onError(error instanceof Error ? error.message : "Could not load Google login.");
      });

    return () => {
      cancelled = true;
    };
  }, [clientId, disabled, onCredential, onError]);

  return (
    <div className="space-y-2">
      <div ref={containerRef} className={rendered && clientId && !disabled ? "flex justify-center" : "hidden"} aria-label={label} />
      {!rendered || !clientId || disabled ? (
        <Button
          type="button"
          variant="outline"
          className="w-full"
          disabled={disabled || authenticating}
          loading={authenticating}
          loadingText={loadingLabel}
          onClick={() => {
            if (!clientId) {
              onError("Google login is not configured yet.");
            }
          }}
        >
          {label}
        </Button>
      ) : null}
      {authenticating ? <p className="text-center text-xs text-foreground/60">{loadingLabel}</p> : null}
    </div>
  );
}
