"use client";

import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { BirthYearInput } from "@/components/linked-learners/birth-year-input";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  redeemLinkedLearnerInvitationLink,
  resolveLinkedLearnerInvitationLink,
  type LinkedLearnerInvitationLinkResolveResponse,
} from "@/lib/api";
import {
  buildLoginPath,
  getAuthUser,
  LOGIN_REASON_AUTH_REQUIRED,
  resolveAuthenticatedHome,
} from "@/lib/auth";
import {
  buildLinkedLearnerInvitationPath,
  clearLinkedLearnerInvitationIntentCookie,
  setLinkedLearnerInvitationIntentCookie,
} from "@/lib/linked-learner-invitation-intent";

const MINIMUM_BIRTH_YEAR = 1900;

export default function LinkedLearnerInvitationPage() {
  const params = useParams<{ token: string }>();
  const router = useRouter();
  const token = params.token;
  const [invitation, setInvitation] = useState<LinkedLearnerInvitationLinkResolveResponse | null>(null);
  const [birthYear, setBirthYear] = useState("");
  const [loading, setLoading] = useState(true);
  const [redeeming, setRedeeming] = useState(false);
  const [redeemed, setRedeemed] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const authUser = getAuthUser();
    const invitationPath = buildLinkedLearnerInvitationPath(token);
    if (!authUser) {
      // Query-string redirects survive login but not signup. The first-party cookie carries only
      // the opaque token through signup, email verification and onboarding, then auth routing
      // reconstructs this exact path.
      setLinkedLearnerInvitationIntentCookie(token);
      router.replace(buildLoginPath({
        redirectTo: invitationPath,
        reason: LOGIN_REASON_AUTH_REQUIRED,
      }));
      return;
    }

    const authenticatedHome = resolveAuthenticatedHome(authUser);
    if (authenticatedHome === "/verify-email" || authenticatedHome === "/onboarding") {
      setLinkedLearnerInvitationIntentCookie(token);
      router.replace(authenticatedHome);
      return;
    }

    clearLinkedLearnerInvitationIntentCookie();
    let cancelled = false;
    void resolveLinkedLearnerInvitationLink(token)
      .then((resolved) => {
        if (!cancelled) setInvitation(resolved);
      })
      .catch((resolveError) => {
        if (!cancelled) {
          setError(resolveError instanceof Error
            ? resolveError.message : "This invitation link is not available.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [router, token]);

  const redeemerIsLearner = invitation?.inviterRole === "SUPPORTER";

  const handleRedeem = async () => {
    let parsedBirthYear: number | null = null;
    if (redeemerIsLearner && birthYear.trim()) {
      parsedBirthYear = Number(birthYear);
      const currentYear = new Date().getFullYear();
      if (birthYear.trim().length !== 4
          || parsedBirthYear < MINIMUM_BIRTH_YEAR
          || parsedBirthYear > currentYear) {
        setError(`Enter a year between ${MINIMUM_BIRTH_YEAR} and ${currentYear}.`);
        return;
      }
    }

    setRedeeming(true);
    setError(null);
    try {
      const result = await redeemLinkedLearnerInvitationLink(token, parsedBirthYear);
      if (result.status !== "PENDING") {
        throw new Error("The connection was not created safely. Please contact support.");
      }
      setRedeemed(true);
    } catch (redeemError) {
      setError(redeemError instanceof Error
        ? redeemError.message : "Could not use this invitation link.");
    } finally {
      setRedeeming(false);
    }
  };

  return (
    <main className="mx-auto w-full max-w-2xl space-y-6 px-4 py-6 sm:px-6">
      <BackLink href="/linked-learners" label="Learning connections" />
      <PageHeader
        eyebrow="Learning support"
        title="Connection invitation"
        description="Both people must agree before a learning connection becomes active."
      />

      <Card className="space-y-4 p-4 sm:p-6">
        {loading ? <p className="text-sm text-foreground/70">Checking this invitation…</p> : null}
        {!loading && error && !invitation ? <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error}</p> : null}
        {invitation && !redeemed ? (
          <>
            <div>
              <h2 className="text-lg font-semibold">Connect with {invitation.inviterName}</h2>
              <p className="mt-1 text-sm text-foreground/70">
                {invitation.inviterRole === "SUPPORTER"
                  ? "They would support your learning."
                  : "They are asking you to support their learning."}
              </p>
            </div>
            <p className="text-sm text-foreground/70">
              Confirming creates a pending connection. {invitation.inviterName} must confirm you before it becomes active.
            </p>
            {redeemerIsLearner ? (
              <label className="block space-y-1.5 text-sm font-medium" htmlFor="invitation-birth-year">
                Your birth year
                <BirthYearInput
                  id="invitation-birth-year"
                  value={birthYear}
                  onChange={setBirthYear}
                />
                <span className="block text-xs font-normal text-foreground/60">
                  Needed only if you have not recorded it before. Until this request is confirmed,
                  the year is held only for its guardian-consent decision. Confirmation makes it
                  your permanent account-level year; revoking the request first deletes it.
                </span>
              </label>
            ) : null}
            {error ? <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error}</p> : null}
            <Button type="button" onClick={() => void handleRedeem()} loading={redeeming}>
              Confirm connection request
            </Button>
          </>
        ) : null}
        {redeemed ? (
          <div className="space-y-3">
            <h2 className="text-lg font-semibold">Request sent</h2>
            <p className="text-sm text-foreground/70">
              The connection is pending until the person who created the link confirms it. If you
              supplied a new birth year, it stays with this pending request until then and is deleted
              if either person revokes first. No learning activity or progress is shared yet.
            </p>
            <Button type="button" onClick={() => router.push("/linked-learners")}>View learning connections</Button>
          </div>
        ) : null}
      </Card>
    </main>
  );
}
