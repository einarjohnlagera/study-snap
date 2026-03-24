"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { completeOnboarding, getMe, type EngagementMode, type ProfileType } from "@/lib/api";
import { getAuthUser, setAuthUser } from "@/lib/auth";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";

const PROFILE_TYPE_OPTIONS: Array<{ value: ProfileType; label: string }> = [
  { value: "STUDENT", label: "Student" },
  { value: "TEACHER", label: "Teacher" },
  { value: "PROFESSIONAL", label: "Professional" },
  { value: "PARENT", label: "Parent" },
];

const LEARNING_STYLE_OPTIONS: Array<{
  value: EngagementMode;
  label: string;
  description: string;
}> = [
  {
    value: "FOCUSED",
    label: "Focused",
    description: "Use NoteLib when you need it. No streaks or pressure.",
  },
  {
    value: "CONSISTENCY",
    label: "Consistency",
    description: "Light encouragement to study regularly.",
  },
  {
    value: "STREAK",
    label: "Streak",
    description: "Track consecutive study days.",
  },
];

export default function OnboardingPage() {
  const router = useRouter();
  const [profileType, setProfileType] = useState<ProfileType | null>(null);
  const [engagementMode, setEngagementMode] = useState<EngagementMode>("FOCUSED");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
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
    if (authUser.onboardingCompletedAt) {
      router.replace("/dashboard");
      return;
    }

    let cancelled = false;
    void getMe()
      .then((me) => {
        if (cancelled) {
          return;
        }
        if (me.onboardingCompletedAt) {
          router.replace("/dashboard");
          return;
        }
        setProfileType(me.profileType ?? null);
        setEngagementMode(me.engagementMode);
      })
      .catch((err) => {
        if (cancelled) {
          return;
        }
        setError(err instanceof Error ? err.message : "Could not load onboarding.");
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [router]);

  const handleContinue = async () => {
    if (!profileType || saving) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const me = await completeOnboarding({ profileType, engagementMode });
      const authUser = getAuthUser();
      if (authUser) {
        setAuthUser({
          ...authUser,
          displayName: me.displayName,
          profileType: me.profileType,
          emailVerifiedAt: me.emailVerifiedAt,
          onboardingCompletedAt: me.onboardingCompletedAt,
        });
      }
      router.push("/dashboard");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not complete onboarding.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="mx-auto w-full max-w-2xl px-4 py-8 sm:px-6 sm:py-12">
      <Card className="space-y-6 p-5 sm:p-7">
        <div className="space-y-2">
          <CardTitle>Let&apos;s set up your study style.</CardTitle>
          <CardDescription>
            This helps NoteLib fit the way you study.
          </CardDescription>
        </div>

        {loading ? (
          <p className="text-sm text-foreground/70">Loading setup…</p>
        ) : (
          <div className="space-y-6">
            <section className="space-y-3">
              <div className="space-y-1">
                <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Profile Type</h2>
                <p className="text-sm text-foreground/70">Choose the one that fits you best.</p>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                {PROFILE_TYPE_OPTIONS.map((option) => (
                  <label
                    key={option.value}
                    className="flex items-center gap-3 rounded-xl border border-border bg-background px-4 py-3 text-sm"
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
            </section>

            <section className="space-y-3">
              <div className="space-y-1">
                <h2 className="text-sm font-semibold uppercase tracking-wide text-foreground/70">Learning Style</h2>
                <p className="text-sm text-foreground/70">Pick the level of study encouragement you want.</p>
              </div>
              <div className="space-y-3">
                {LEARNING_STYLE_OPTIONS.map((option) => (
                  <label
                    key={option.value}
                    className="flex cursor-pointer items-start gap-3 rounded-xl border border-border bg-background px-4 py-3"
                  >
                    <input
                      type="radio"
                      name="engagementMode"
                      value={option.value}
                      checked={engagementMode === option.value}
                      onChange={() => setEngagementMode(option.value)}
                    />
                    <span className="space-y-1">
                      <span className="block text-sm font-medium">{option.label}</span>
                      <span className="block text-xs text-foreground/60">{option.description}</span>
                    </span>
                  </label>
                ))}
              </div>
            </section>
          </div>
        )}

        {error ? <p className="text-sm text-red-600 dark:text-red-400">{error}</p> : null}
        <Button
          type="button"
          onClick={() => void handleContinue()}
          disabled={loading || saving || !profileType}
          className="w-full sm:w-auto"
        >
          {saving ? "Saving..." : "Finish setup"}
        </Button>
      </Card>
    </div>
  );
}
