"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { BirthYearInput } from "@/components/linked-learners/birth-year-input";
import { BackLink } from "@/components/ui/back-link";
import { Button, buttonVariants } from "@/components/ui/button";
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
import {
  getLinkedLearnerRedemptionCompletion,
  setLinkedLearnerRedemptionCompletion,
} from "@/lib/linked-learner-redemption-completion";

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
  const [alreadySent, setAlreadySent] = useState(false);
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

    // ⚠️ Do NOT clear the resume cookie before resolving. Found by the pre-signoff pressure test:
    // the route guard above keys on `needsOnboarding`, which deliberately treats two account
    // classes as onboarded while the SERVER's onboardingCompletedAt is still null — a failed
    // completeOnboarding POST, and the copy-on-signup cohort whose dashboard prompt is dismissible.
    // For those, resolve now returns 403 ONBOARDING_REQUIRED. Clearing first destroyed the token,
    // so the person lost the invitation with no route back. Clearing on success instead lets the
    // 403 self-heal: they finish onboarding and the dashboard consumer resumes this same token.
    let cancelled = false;
    void resolveLinkedLearnerInvitationLink(token)
      .then((resolved) => {
        clearLinkedLearnerInvitationIntentCookie();
        if (!cancelled) setInvitation(resolved);
      })
      .catch((resolveError) => {
        if (!cancelled) {
          // ⚠️ THIS is what makes the token actually survive, and it was added after a cold re-audit
          // showed the earlier fix only DELAYED the loss. Keeping the cookie past a 403 is not
          // enough: the only automatic route onward is /dashboard, whose intent consumer is
          // one-shot and clears the cookie before navigating — so the second 403 burned it.
          // The server's ONBOARDING_REQUIRED carries ACTION=COMPLETE_ONBOARDING precisely so a
          // client can route on it; nothing read it until now. Sending them to /onboarding with the
          // cookie intact is the only path that genuinely self-heals: they finish, and auth routing
          // reconstructs this exact invitation path.
          // ⚠️ Duck-typed on `action`, NOT `instanceof ApiRequestError`. Class identity does not
          // survive every module boundary — it is undefined under the api mock, where the
          // instanceof form threw and took four unrelated tests down with it — and the remedy code
          // is the actual contract here, not the class.
          const remedy = resolveError instanceof Error
            ? (resolveError as { action?: string | null }).action
            : null;
          if (remedy === "COMPLETE_ONBOARDING") {
            setLinkedLearnerInvitationIntentCookie(token);
            router.replace("/onboarding");
            return;
          }
          try {
            const completion = getLinkedLearnerRedemptionCompletion();
            if (completion?.token === token && completion.userId === authUser.id) {
              // ⚠️ Do NOT clear the marker here. Clearing on first read makes this fix work
              // exactly once: the next reload falls through to the dead-link error, so the same
              // action gets two contradictory answers. Let it expire on its own max-age instead.
              setAlreadySent(true);
              return;
            }
          } catch {
            // A blocked cookie jar must fail closed to the generic not-found state.
          }
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
      try {
        setLinkedLearnerRedemptionCompletion(token, getAuthUser()?.id ?? "");
      } catch {
        // This marker only improves this browser's reload copy; it must never affect redemption.
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
              <div className="space-y-1.5 text-sm font-medium">
                <label className="block" htmlFor="invitation-birth-year">Your birth year</label>
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
              </div>
            ) : null}
            {error ? <p role="alert" className="text-sm text-red-700 dark:text-red-300">{error}</p> : null}
            <Button type="button" onClick={() => void handleRedeem()} loading={redeeming}>
              Confirm connection request
            </Button>
          </>
        ) : null}
        {redeemed || alreadySent ? (
          <div className="space-y-3">
            <h2 className="text-lg font-semibold">Request sent</h2>
            {redeemed ? (
              // Stated immediately after the redemption call returned PENDING, which is asserted
              // above — so this branch may describe the connection's state.
              <p className="text-sm text-foreground/70">
                The connection is pending until the person who created the link confirms it. If you
                supplied a new birth year, it stays with this pending request until then and is
                deleted if either person revokes first. No learning activity or progress is shared
                yet.
              </p>
            ) : (
              // ⚠️ A RELOAD KNOWS ONLY THAT THIS BROWSER SENT THE REQUEST — never its state now.
              // The creator may have confirmed since, so asserting "pending" or "nothing is shared
              // yet" here would be the same defect this item exists to close: reporting state the
              // surface cannot see. State the past fact and point at the page that does know.
              <p className="text-sm text-foreground/70">
                You already sent this request from this browser. Open your learning connections to
                see where it stands now.
              </p>
            )}
            <Link className={buttonVariants({})} href="/linked-learners">View learning connections</Link>
          </div>
        ) : null}
      </Card>
    </main>
  );
}
