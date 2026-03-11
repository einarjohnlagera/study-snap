"use client";

import { FormEvent, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { login, signup } from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";

type Mode = "login" | "signup";

function nextRouteForAuth(profileType: "STUDENT" | "PARENT" | "PROFESSIONAL" | null, emailVerifiedAt: string | null) {
  if (!emailVerifiedAt) {
    return "/verify-email";
  }
  if (!profileType) {
    return "/onboarding";
  }
  return "/dashboard";
}

export default function AuthPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [firstName, setFirstName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [keepSignedIn, setKeepSignedIn] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser) {
      return;
    }
    router.replace(nextRouteForAuth(authUser.profileType, authUser.emailVerifiedAt));
  }, [router]);

  const canSubmit = useMemo(() => {
    if (mode === "signup") {
      return firstName.trim().length > 0 && email.trim().length > 0 && password.trim().length >= 8;
    }
    return email.trim().length > 0 && password.trim().length > 0;
  }, [email, firstName, mode, password]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!canSubmit || loading) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const authUser =
        mode === "signup"
          ? await signup({
              firstName,
              email,
              password,
              displayName: displayName.trim().length > 0 ? displayName : undefined,
            })
          : await login({ email, password, keepSignedIn });
      setAuthUser(authUser);
      router.push(nextRouteForAuth(authUser.profileType, authUser.emailVerifiedAt));
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not continue. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-xl px-4 py-6 sm:px-6 sm:py-10">
      <Card className="space-y-6 p-4 sm:p-6">
        <div className="space-y-2">
          <CardTitle>{mode === "login" ? "Log in to Study Snap" : "Create your Study Snap account"}</CardTitle>
          <CardDescription>
            {mode === "login"
              ? "Continue to your study workspace."
              : "Sign up to generate and save Study Packs."}
          </CardDescription>
        </div>

        <div className="grid grid-cols-2 gap-2">
          <Button
            type="button"
            variant={mode === "login" ? "default" : "outline"}
            className="w-full"
            onClick={() => setMode("login")}
          >
            Log in
          </Button>
          <Button
            type="button"
            variant={mode === "signup" ? "default" : "outline"}
            className="w-full"
            onClick={() => setMode("signup")}
          >
            Sign up
          </Button>
        </div>

        <form className="space-y-4" onSubmit={handleSubmit}>
          {mode === "signup" ? (
            <>
              <label className="block space-y-2">
                <span className="text-sm font-medium">First Name</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  autoComplete="given-name"
                  required
                />
              </label>
              <label className="block space-y-2">
                <span className="text-sm font-medium">Display Name (optional)</span>
                <input
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder={firstName.trim() || "How your name appears"}
                  autoComplete="nickname"
                />
              </label>
            </>
          ) : null}

          <label className="block space-y-2">
            <span className="text-sm font-medium">Email</span>
            <input
              type="email"
              className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
              required
            />
          </label>

          <label className="block space-y-2">
            <span className="text-sm font-medium">Password</span>
            <input
              type="password"
              className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === "signup" ? "new-password" : "current-password"}
              required
            />
          </label>
          {mode === "login" ? (
            <label className="flex items-start gap-2 text-sm">
              <input
                type="checkbox"
                checked={keepSignedIn}
                onChange={(e) => setKeepSignedIn(e.target.checked)}
              />
              Keep me signed in for 30 days
            </label>
          ) : null}

          {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}

          <Button type="submit" className="w-full sm:w-auto" disabled={!canSubmit || loading}>
            {loading ? "Please wait..." : mode === "login" ? "Log in" : "Create account"}
          </Button>
        </form>
      </Card>
    </div>
  );
}
