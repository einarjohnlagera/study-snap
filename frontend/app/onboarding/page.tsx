"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { completeOnboardingProfileType, type ProfileType } from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";

const OPTIONS: Array<{ value: ProfileType; label: string }> = [
  { value: "STUDENT", label: "Student" },
  { value: "PARENT", label: "Parent" },
  { value: "PROFESSIONAL", label: "Professional" },
];

export default function OnboardingPage() {
  const router = useRouter();
  const [profileType, setProfileType] = useState<ProfileType | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser) {
      redirectToLoginWithCurrentDestination(router);
      return;
    }
    if (!authUser.emailVerifiedAt) {
      router.replace("/verify-email");
      return;
    }
    if (authUser.profileType) {
      router.replace("/dashboard");
    }
  }, [router]);

  const handleContinue = async () => {
    if (!profileType || loading) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const me = await completeOnboardingProfileType({ profileType });
      const authUser = getAuthUser();
      if (authUser) {
        setAuthUser({
          ...authUser,
          profileType: me.profileType,
          displayName: me.displayName,
          emailVerifiedAt: me.emailVerifiedAt,
        });
      }
      router.push("/dashboard");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not complete onboarding.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-xl px-6 py-10">
      <Card className="space-y-5">
        <div className="space-y-2">
          <CardTitle>Welcome to Study Snap!</CardTitle>
          <CardDescription>I'm using Study Snap as a:</CardDescription>
        </div>
        <div className="space-y-2">
          {OPTIONS.map((option) => (
            <label
              key={option.value}
              className="flex items-center gap-3 rounded-lg border border-border bg-background px-3 py-2 text-sm"
            >
              <input
                type="radio"
                name="profileType"
                value={option.value}
                checked={profileType === option.value}
                onChange={() => setProfileType(option.value)}
              />
              {option.label}
            </label>
          ))}
        </div>
        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        <Button type="button" onClick={() => void handleContinue()} disabled={!profileType || loading}>
          {loading ? "Saving..." : "Continue"}
        </Button>
      </Card>
    </div>
  );
}
