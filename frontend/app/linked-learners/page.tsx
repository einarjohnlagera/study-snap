"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { PageHeader } from "@/components/page-header";
import { BirthYearInput } from "@/components/linked-learners/birth-year-input";
import { ResponsiveActionLink } from "@/components/ui/action-button";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
import { Toggle } from "@/components/ui/toggle";
import { describeSupportedLearnerStatus, LINKED_LEARNER_STATUS_COPY } from "@/lib/linked-learner-status";
import {
  acceptLinkedLearner,
  ApiRequestError,
  acceptLinkedLearnerInvitation,
  createLinkedLearnerInvitationLink,
  listLinkedLearnerInvitations,
  listLinkedLearnerInvitationLinks,
  revokeLinkedLearnerInvitation,
  revokeLinkedLearnerInvitationLink,
  type LinkedLearnerInvitationLinkResponse,
  type LinkedLearnerInvitationResponse,
  correctLinkedLearnerBirthYear,
  getLinkedLearners,
  getLinkedLearnerActivity,
  inviteLinkedLearner,
  previewLinkedLearnerBirthYearCorrection,
  recordLinkedLearnerBirthYear,
  recordLinkedLearnerGuardianConsent,
  revokeLinkedLearner,
  setLinkedLearnerActivityGrant,
  setLinkedLearnerProgressGrant,
  type LinkedLearnerActivityResponse,
  type LinkedLearnerResponse,
  type LinkedLearnerSide,
} from "@/lib/api";

const INPUT_CLASSES = "h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary";

const MIN_BIRTH_YEAR = 1900;

/** Single source for the range message, so live feedback and submit validation cannot diverge. */
function birthYearRangeError(rawYear: string): string | null {
  const currentYear = new Date().getFullYear();
  const year = Number(rawYear);
  if (!Number.isFinite(year) || year < MIN_BIRTH_YEAR || year > currentYear) {
    return `Enter a year between ${MIN_BIRTH_YEAR} and ${currentYear}.`;
  }
  return null;
}

function formatDate(value: string | null) {
  if (!value) return null;
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(new Date(value));
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function engagementModeLabel(mode: LinkedLearnerActivityResponse["engagementMode"]): string {
  if (mode === "STREAK") return "Streak";
  if (mode === "CONSISTENCY") return "Consistency";
  return "Focused";
}

function SharingPanel({
  link,
  onActivityGrantUpdated,
  onProgressGrantUpdated,
  onAccessEnded,
  onFailure,
}: Readonly<{
  link: LinkedLearnerResponse;
  onActivityGrantUpdated: (granted: boolean) => void;
  onProgressGrantUpdated: (granted: boolean) => void;
  onAccessEnded: () => Promise<void>;
  onFailure: (message: string) => void;
}>) {
  const [activitySaving, setActivitySaving] = useState(false);
  const [progressSaving, setProgressSaving] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [activity, setActivity] = useState<LinkedLearnerActivityResponse | null>(null);
  const [activityError, setActivityError] = useState<string | null>(null);
  const [activityLoading, setActivityLoading] = useState(false);

  const loadActivity = useCallback(async () => {
    setActivityLoading(true);
    setActivityError(null);
    try {
      setActivity(await getLinkedLearnerActivity(link.id));
    } catch (loadError) {
      if (loadError instanceof ApiRequestError && (loadError.status === 403 || loadError.status === 404)) {
        setExpanded(false);
        setActivity(null);
        await onAccessEnded();
        return;
      }
      setActivity(null);
      setActivityError(errorMessage(loadError, "Could not load this study activity."));
    } finally {
      setActivityLoading(false);
    }
  }, [link.id, onAccessEnded]);

  const handleToggle = async (granted: boolean) => {
    setActivitySaving(true);
    try {
      const response = await setLinkedLearnerActivityGrant(link.id, granted);
      onActivityGrantUpdated(response.granted);
    } catch (saveError) {
      // The rendered value remains the last server-confirmed value. For a privacy control, a
      // failed write must never leave an optimistic state displayed as if it were persisted.
      onFailure(errorMessage(saveError, "Could not update activity sharing. Your previous setting was restored."));
    } finally {
      setActivitySaving(false);
    }
  };

  const handleProgressToggle = async (granted: boolean) => {
    setProgressSaving(true);
    try {
      const response = await setLinkedLearnerProgressGrant(link.id, granted);
      onProgressGrantUpdated(response.granted);
    } catch (saveError) {
      // Keep the controlled toggle on the last value the server confirmed.
      onFailure(errorMessage(saveError, "Could not update progress sharing. Your previous setting was restored."));
    } finally {
      setProgressSaving(false);
    }
  };

  const paused = link.status === "PENDING";

  const toggleActivityView = () => {
    const nextExpanded = !expanded;
    setExpanded(nextExpanded);
    if (nextExpanded) {
      // ⚠️ ALWAYS refetch on expand — never re-render momentum from memory.
      // Access is re-derived server-side on every request, and that is what makes a revoke cut
      // immediately. Serving a cached `activity` on re-expand defeats it CLIENT-side: collapse,
      // the owner revokes, re-expand, and withdrawn data renders with no request issued, so no
      // 403 arrives and the access-ended path never runs. On a privacy control the cheap read is
      // the correct trade.
      void loadActivity();
    } else {
      // Drop the payload on collapse so it cannot outlive the grant that authorized it.
      setActivity(null);
      setActivityError(null);
    }
  };

  return (
    <div className="space-y-3 rounded-lg border border-border p-3 sm:p-4">
      {paused ? (
        <p className="rounded-md bg-amber-50 p-2 text-sm text-amber-950 dark:bg-amber-950/30 dark:text-amber-100">
          {/*
            ⚠️ STATUS, not access — the same rule the two strings below already follow. This banner
            used to promise that "existing sharing choices resume when the connection is active",
            which is false for a PENDING row that was never accepted at all — the state a link
            redemption newly creates, where nothing ever started to resume. We cannot tell the two
            apart: pauseAcceptedForConsent writes accepted_at = null, so a paused relationship is
            indistinguishable from a never-accepted one in this DTO. So describe the status and the
            caller's own choice, and promise nothing about what happens next.
          */}
          This connection is pending, so nothing is being shared right now. The switches below show
          your own choices — you can turn them off at any time.
        </p>
      ) : null}
      <div className="flex items-center justify-between gap-4">
        <label htmlFor={`activity-sharing-${link.id}`} className="text-sm font-medium">
          Share my study activity with {link.counterpartyDisplayName}
        </label>
        <Toggle
          id={`activity-sharing-${link.id}`}
          checked={link.activitySharedByMe}
          onChange={(granted) => void handleToggle(granted)}
          ariaLabel={`Share my study activity with ${link.counterpartyDisplayName}`}
          disabled={activitySaving || (paused && !link.activitySharedByMe)}
        />
      </div>
      {link.callerRole === "LEARNER" ? (
        <div className="flex items-center justify-between gap-4 border-t border-border pt-3">
          <label htmlFor={`progress-sharing-${link.id}`} className="text-sm font-medium">
            Share my study progress with {link.counterpartyDisplayName}
          </label>
          <Toggle
            id={`progress-sharing-${link.id}`}
            checked={link.progressSharedByMe}
            onChange={(granted) => void handleProgressToggle(granted)}
            ariaLabel={`Share my study progress with ${link.counterpartyDisplayName}`}
            disabled={progressSaving || (paused && !link.progressSharedByMe)}
          />
        </div>
      ) : (
        <div className="border-t border-border pt-3">
          <p className="text-sm text-foreground/70">
            {paused
              // ⚠️ STATUS, not access. The DTO zeroes `*SharedWithMe` on a non-ACCEPTED row, so we
              // cannot tell "granted, now paused" from "never granted" — claiming either is a guess.
              ? `Sharing is paused while this connection is inactive`
              : link.progressSharedWithMe
                ? `${link.counterpartyDisplayName} shares their study progress with you`
                : `${link.counterpartyDisplayName} does not share their study progress with you`}
          </p>
        </div>
      )}
      <div className="border-t border-border pt-3">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-foreground/70">
            {paused
              ? `Sharing is paused while this connection is inactive`
              : link.activitySharedWithMe
              ? `${link.counterpartyDisplayName} shares their study activity with you`
              : `${link.counterpartyDisplayName} does not share their study activity with you`}
          </p>
          {link.activitySharedWithMe ? (
            <Button
              type="button"
              variant="outline"
              aria-expanded={expanded}
              onClick={toggleActivityView}
            >
              {expanded ? "Hide momentum" : "View momentum"}
            </Button>
          ) : null}
        </div>

        {/* ⚠️ Gated on the grant as well as on `expanded`: the control that closes this panel lives
            inside the `activitySharedWithMe` branch, so a refresh that flips the grant false would
            otherwise strand an open panel on screen with no way to dismiss it. */}
        {expanded && link.activitySharedWithMe ? (
          <div className="mt-3 rounded-lg bg-muted/50 p-3" aria-label={`${link.counterpartyDisplayName}'s momentum`}>
            {activityLoading ? <Skeleton className="h-20 w-full" /> : null}
            {!activityLoading && activityError ? (
              <div className="space-y-2">
                <p role="alert" className="text-sm text-red-700 dark:text-red-300">{activityError}</p>
                <Button type="button" variant="outline" onClick={() => void loadActivity()}>Retry</Button>
              </div>
            ) : null}
            {!activityLoading && activity ? (
              <div className="grid gap-3 sm:grid-cols-4">
                <div><p className="text-xs text-foreground/60">Current streak</p><p className="font-semibold">{activity.currentStreak} days</p></div>
                <div><p className="text-xs text-foreground/60">Longest streak</p><p className="font-semibold">{activity.longestStreak} days</p></div>
                <div><p className="text-xs text-foreground/60">This week</p><p className="font-semibold">{activity.studyDaysThisWeek} study days</p></div>
                <div><p className="text-xs text-foreground/60">Study mode</p><p className="font-semibold">{engagementModeLabel(activity.engagementMode)}</p></div>
                {activity.currentStreak === 0 && activity.longestStreak === 0 && activity.studyDaysThisWeek === 0 ? (
                  <p className="text-sm text-foreground/70 sm:col-span-4">No meaningful study activity recorded yet.</p>
                ) : null}
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}

export default function LinkedLearnersPage() {
  const [links, setLinks] = useState<LinkedLearnerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [email, setEmail] = useState("");
  const [inviterRole, setInviterRole] = useState<LinkedLearnerSide>("SUPPORTER");
  const router = useRouter();
  const [birthYears, setBirthYears] = useState<Record<string, string>>({});
  const [correctedBirthYear, setCorrectedBirthYear] = useState("");
  const [correctionWarningCount, setCorrectionWarningCount] = useState<number | null>(null);
  const [correctionYearError, setCorrectionYearError] = useState<string | null>(null);
  const [consentChecked, setConsentChecked] = useState<Record<string, boolean>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [inviting, setInviting] = useState(false);
  const [correctingBirthYear, setCorrectingBirthYear] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Field-level, because a form whose only invalid field is a permanent declaration should say WHICH
  // field is wrong. A disabled submit button cannot, and a toast names the problem away from the input.
  const [fieldErrors, setFieldErrors] = useState<{ email: string | null; birthYear: string | null }>({
    email: null,
    birthYear: null,
  });
  const emailInputRef = useRef<HTMLInputElement | null>(null);
  const birthYearInputRef = useRef<HTMLInputElement | null>(null);
  const inviteFormRef = useRef<HTMLFormElement | null>(null);

  const loadLinks = useCallback(async () => {
    try {
      setLinks(await getLinkedLearners());
      setError(null);
    } catch (loadError) {
      setError(errorMessage(loadError, "Could not load your learning connections."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadLinks();
  }, [loadLinks]);

  const [invitations, setInvitations] = useState<LinkedLearnerInvitationResponse[]>([]);
  const [invitationsLoading, setInvitationsLoading] = useState(true);
  const [invitationListError, setInvitationListError] = useState<string | null>(null);
  const [invitationLinks, setInvitationLinks] = useState<LinkedLearnerInvitationLinkResponse[]>([]);
  const [invitationLinksLoading, setInvitationLinksLoading] = useState(true);
  const [invitationLinkError, setInvitationLinkError] = useState<string | null>(null);
  // ⚠️ Separate from the page-level `notice`, which renders two cards below this one — far enough
  // that feedback for a link action read as no feedback at all.
  const [invitationLinkNotice, setInvitationLinkNotice] = useState<string | null>(null);
  const [linkCreatorRole, setLinkCreatorRole] = useState<LinkedLearnerSide>("SUPPORTER");
  const [linkBirthYear, setLinkBirthYear] = useState("");
  const [creatingInvitationLink, setCreatingInvitationLink] = useState(false);
  const [busyInvitationLinkId, setBusyInvitationLinkId] = useState<string | null>(null);
  // Captured up front only when the inviter IS the learner: the supporter accepts later, and the
  // consent gate needs the learner's own year, which only the learner may declare.
  const [inviteBirthYear, setInviteBirthYear] = useState("");

  const loadInvitations = useCallback(async () => {
    try {
      setInvitations(await listLinkedLearnerInvitations());
      setInvitationListError(null);
    } catch (loadError) {
      // A failed invitation load must not render an empty or partial list as if it were complete.
      setInvitations([]);
      setInvitationListError(errorMessage(loadError, "Could not load invitations."));
    } finally {
      setInvitationsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadInvitations();
  }, [loadInvitations]);

  const loadInvitationLinks = useCallback(async (preserveOnError = false) => {
    try {
      setInvitationLinks(await listLinkedLearnerInvitationLinks());
      setInvitationLinkError(null);
    } catch (loadError) {
      if (preserveOnError) {
        // A focus refresh is opportunistic. Keep the last server-confirmed list visible through a
        // transient background failure, and say plainly that it may now be stale.
        const message = errorMessage(loadError, "Could not refresh invitation links.");
        setInvitationLinkError(`${message} Showing the last loaded list.`);
      } else {
        // A failed invitation-link load must not render an empty or partial list as if complete.
        setInvitationLinks([]);
        setInvitationLinkError(errorMessage(loadError, "Could not load invitation links."));
      }
    } finally {
      setInvitationLinksLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadInvitationLinks();
  }, [loadInvitationLinks]);

  useEffect(() => {
    const refreshInvitationLinks = () => {
      void loadInvitationLinks(true);
    };
    globalThis.addEventListener("focus", refreshInvitationLinks);
    return () => {
      globalThis.removeEventListener("focus", refreshInvitationLinks);
    };
  }, [loadInvitationLinks]);

  const handleCreateInvitationLink = async (event: React.FormEvent) => {
    event.preventDefault();
    const rawYear = linkBirthYear.trim();
    if (linkCreatorRole === "LEARNER" && rawYear) {
      const rangeError = rawYear.length === 4 ? birthYearRangeError(rawYear) : "Enter all four digits, like 2011.";
      if (rangeError) {
        setInvitationLinkError(rangeError);
        return;
      }
    }
    setCreatingInvitationLink(true);
    setInvitationLinkError(null);
    setInvitationLinkNotice(null);
    try {
      await createLinkedLearnerInvitationLink(
        linkCreatorRole,
        linkCreatorRole === "LEARNER" && rawYear ? Number(rawYear) : null,
      );
      await loadInvitationLinks();
      setLinkBirthYear("");
      setInvitationLinkNotice("Invitation link created. It can be used once.");
    } catch (createError) {
      setInvitationLinkError(errorMessage(createError, "Could not create the invitation link."));
    } finally {
      setCreatingInvitationLink(false);
    }
  };

  const handleCopyInvitationLink = async (link: LinkedLearnerInvitationLinkResponse) => {
    setInvitationLinkError(null);
    setInvitationLinkNotice(null);
    try {
      await globalThis.navigator.clipboard.writeText(link.url);
      setInvitationLinkNotice("Invitation link copied.");
    } catch {
      setInvitationLinkError("Could not copy the link. Select the URL and copy it manually.");
    }
  };

  const handleRevokeInvitationLink = async (link: LinkedLearnerInvitationLinkResponse) => {
    setBusyInvitationLinkId(link.id);
    setInvitationLinkError(null);
    setInvitationLinkNotice(null);
    try {
      await revokeLinkedLearnerInvitationLink(link.id);
      await loadInvitationLinks();
      setInvitationLinkNotice("Invitation link revoked.");
    } catch (revokeError) {
      if (revokeError instanceof ApiRequestError && revokeError.status === 404) {
        await loadInvitationLinks();
        setInvitationLinkNotice("Invitation link was already gone.");
      } else {
        // No optimistic removal: a failed privacy write leaves the last server-confirmed list.
        setInvitationLinkError(errorMessage(revokeError, "Could not revoke the invitation link."));
      }
    } finally {
      setBusyInvitationLinkId(null);
    }
  };

  const handleAcceptInvitation = async (invitation: LinkedLearnerInvitationResponse) => {
    setBusyId(invitation.id);
    setError(null);
    try {
      const rawYear = birthYears[invitation.id]?.trim();
      const birthYear = rawYear ? Number(rawYear) : null;
      const updated = await acceptLinkedLearnerInvitation(
        invitation.id, birthYear, consentChecked[invitation.id] === true);
      if (updated.status === "PENDING" && updated.guardianConsentRequired && !updated.guardianConsentRecorded) {
        setNotice("Guardian confirmation is needed before this connection becomes active.");
      } else {
        setNotice("Learning connection accepted.");
      }
      await Promise.all([loadLinks(), loadInvitations()]);
    } catch (acceptError) {
      // ⚠️ SELF-HEAL, and it is part of item 1 rather than a follow-up. Accepting now requires a
      // finished onboarding, and the route guard for this page keys on needsOnboarding(), which
      // treats two cohorts as onboarded while the server column is still null — a failed
      // completeOnboarding POST, and the copy-on-signup cohort whose prompt is dismissible. Without
      // this they would read "Could not accept the invitation" with no idea what to do.
      // ⚠️ Simpler than the invitation-link path's equivalent, and deliberately so: there is no token
      // to preserve. The invitation is server-side and listInvitations was NOT tightened, so it is
      // still listed when they come back and Accept simply works.
      if ((acceptError as { action?: string | null } | null)?.action === "COMPLETE_ONBOARDING") {
        router.replace("/onboarding");
        return;
      }
      setError(errorMessage(acceptError, "Could not accept the invitation."));
    } finally {
      setBusyId(null);
    }
  };

  const handleWithdrawInvitation = async (invitation: LinkedLearnerInvitationResponse) => {
    setBusyId(invitation.id);
    setError(null);
    try {
      // ⚠️ Report the SERVER's outcome, not an assumed one. The counterparty may have accepted
      // between render and click, in which case a relationship now exists and "Invitation
      // withdrawn." would be a claim this client never verified. Refresh BOTH lists for the same
      // reason: withdrawing only reloaded invitations, so a connection created in that window
      // stayed invisible until a manual reload.
      const result = await revokeLinkedLearnerInvitation(invitation.id);
      setNotice(result.message ?? "Invitation withdrawn.");
      await Promise.all([loadInvitations(), loadLinks()]);
    } catch (revokeError) {
      setError(errorMessage(revokeError, "Could not withdraw the invitation."));
    } finally {
      setBusyId(null);
    }
  };

  const handleInviteAgain = (invitation: LinkedLearnerInvitationResponse) => {
    setEmail(invitation.invitedEmail);
    // ⚠️ Re-arm writes inviter_role again. Preserving the expired row's role here prevents the
    // existing relationship-to-be from silently flipping direction when the invite is submitted.
    setInviterRole(invitation.inviterRole);
    setFieldErrors({ email: null, birthYear: null });
    setError(null);
    setNotice("Review the invitation, then send it again. This will send another email.");
    inviteFormRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
    emailInputRef.current?.focus();
  };

  const replaceLink = (updated: LinkedLearnerResponse) => {
    setLinks((current) => current.map((link) => link.id === updated.id ? updated : link));
  };

  /**
   * Merge specific fields into one link, inside the state updater.
   *
   * <p>⚠️ Use this rather than `replaceLink({ ...link, field })` whenever the caller is an event
   * handler. `link` there is the snapshot from the render that created the handler, so spreading it
   * writes back every OTHER field as it looked at click time — silently reverting anything a
   * concurrent `loadLinks()` refreshed in between (status, the counterparty's grant, consent state).
   */
  const updateLinkFields = (id: string, fields: Partial<LinkedLearnerResponse>) => {
    setLinks((current) => current.map((link) => link.id === id ? { ...link, ...fields } : link));
  };

  /**
   * ⚠️ There is deliberately NO default birth year.
   *
   * <p>`users.birth_year` is account-global and effectively write-once — it drives guardian consent for
   * every connection the account will ever form, and only the learner can correct it. A pre-filled value is
   * a declaration nobody made: tab past it and you have silently asserted an age. Defaulting young puts
   * every adult under the consent threshold; defaulting old disables the gate the threshold exists for.
   * Collecting it at link time instead of signup is pointless if the field answers itself.
   */
  const validateInvite = (): boolean => {
    const trimmedEmail = email.trim();
    const nextErrors: { email: string | null; birthYear: string | null } = { email: null, birthYear: null };

    if (!trimmedEmail) {
      nextErrors.email = "Enter the email address of the person you want to invite.";
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      nextErrors.email = "That does not look like an email address.";
    }

    if (inviterRole === "LEARNER") {
      const rawYear = inviteBirthYear.trim();
      // ⚠️ Blank is NOT a client-side error. The year is required only when this account has none
      // recorded yet, and the client cannot know that before the first connection exists — the server
      // owns that decision and its message surfaces in the form's error banner. Validating a blank here
      // would block a returning learner who declared their year on an earlier connection.
      if (!rawYear) {
        nextErrors.birthYear = null;
      } else if (rawYear.length !== 4) {
        nextErrors.birthYear = "Enter all four digits, like 2011.";
      } else {
        // Mirrors the server bound (MINIMUM_BIRTH_YEAR..current year) so a rejection is explained here
        // rather than arriving as a generic failure after the request.
        nextErrors.birthYear = birthYearRangeError(rawYear);
      }
    }

    setFieldErrors(nextErrors);
    if (nextErrors.email) {
      emailInputRef.current?.focus();
      return false;
    }
    if (nextErrors.birthYear) {
      birthYearInputRef.current?.focus();
      return false;
    }
    return true;
  };

  const handleInvite = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setNotice(null);
    if (!validateInvite()) {
      return;
    }
    setInviting(true);
    try {
      const response = await inviteLinkedLearner(
        email, inviterRole,
        inviterRole === "LEARNER" && inviteBirthYear.trim() ? Number(inviteBirthYear.trim()) : null);
      setNotice(response.message);
      setEmail("");
      setInviteBirthYear("");
      setFieldErrors({ email: null, birthYear: null });
      await Promise.all([loadLinks(), loadInvitations()]);
    } catch (inviteError) {
      // ⚠️ Same self-heal as the accept path, and it ships WITH the gate rather than after it.
      // Inviting now requires a finished onboarding, and this page's guard keys on
      // needsOnboarding(), which treats two cohorts as onboarded while the server column is null —
      // a failed completeOnboarding POST and the copy-on-signup cohort. Without this they would
      // read "Could not send the invitation" with no idea what to do.
      if ((inviteError as { action?: string | null } | null)?.action === "COMPLETE_ONBOARDING") {
        router.replace("/onboarding");
        return;
      }
      setError(errorMessage(inviteError, "Could not send the invitation."));
    } finally {
      setInviting(false);
    }
  };

  const handleAccept = async (link: LinkedLearnerResponse) => {
    setBusyId(link.id);
    setError(null);
    try {
      const rawYear = birthYears[link.id]?.trim();
      const birthYear = rawYear ? Number(rawYear) : null;
      const updated = await acceptLinkedLearner(link.id, birthYear, consentChecked[link.id] === true);
      replaceLink(updated);
      if (updated.status === "PENDING" && updated.guardianConsentRequired && !updated.guardianConsentRecorded) {
        setNotice("Guardian consent must be recorded before this invitation can be accepted.");
      } else {
        setNotice("Learning connection accepted.");
      }
    } catch (acceptError) {
      setError(errorMessage(acceptError, "Could not accept the invitation."));
    } finally {
      setBusyId(null);
    }
  };

  const handleBirthYear = async (link: LinkedLearnerResponse) => {
    const rawYear = birthYears[link.id]?.trim();
    if (!rawYear) {
      setError("Enter your birth year.");
      return;
    }
    setBusyId(link.id);
    setError(null);
    try {
      replaceLink(await recordLinkedLearnerBirthYear(link.id, Number(rawYear)));
      setNotice("Birth year saved for this connection.");
    } catch (birthYearError) {
      setError(errorMessage(birthYearError, "Could not save the birth year."));
    } finally {
      setBusyId(null);
    }
  };

  const handleConsent = async (link: LinkedLearnerResponse) => {
    if (!consentChecked[link.id]) {
      setError("Confirm the guardian consent attestation first.");
      return;
    }
    setBusyId(link.id);
    setError(null);
    try {
      replaceLink(await recordLinkedLearnerGuardianConsent(link.id));
      setNotice("Guardian consent recorded. The invited person can now accept.");
    } catch (consentError) {
      setError(errorMessage(consentError, "Could not record guardian consent."));
    } finally {
      setBusyId(null);
    }
  };

  const applyBirthYearCorrection = async () => {
    setCorrectingBirthYear(true);
    setError(null);
    try {
      setLinks(await correctLinkedLearnerBirthYear(Number(correctedBirthYear)));
      setCorrectionWarningCount(null);
      setCorrectedBirthYear("");
      setNotice("Your birth year was corrected. Connections that now need guardian consent have been paused.");
    } catch (correctionError) {
      setError(errorMessage(correctionError, "Could not correct your birth year."));
    } finally {
      setCorrectingBirthYear(false);
    }
  };

  const handleBirthYearCorrection = async (event: React.FormEvent) => {
    event.preventDefault();
    // Field-level, matching the invite form. The page-level banner is for request outcomes, not for
    // telling someone the value under their cursor is wrong.
    const rawYear = correctedBirthYear.trim();
    if (!rawYear) {
      setCorrectionYearError("Enter your corrected birth year.");
      return;
    }
    if (rawYear.length !== 4) {
      setCorrectionYearError("Enter all four digits, like 2011.");
      return;
    }
    const rangeError = birthYearRangeError(rawYear);
    if (rangeError) {
      setCorrectionYearError(rangeError);
      return;
    }
    setCorrectionYearError(null);
    setCorrectingBirthYear(true);
    setError(null);
    setNotice(null);
    try {
      const preview = await previewLinkedLearnerBirthYearCorrection(Number(correctedBirthYear));
      if (preview.affectedConnectionCount > 0) {
        setCorrectionWarningCount(preview.affectedConnectionCount);
        return;
      }
      await applyBirthYearCorrection();
    } catch (correctionError) {
      setError(errorMessage(correctionError, "Could not check this birth year correction."));
    } finally {
      setCorrectingBirthYear(false);
    }
  };

  const handleRevoke = async (link: LinkedLearnerResponse) => {
    const previous = links;
    setLinks((current) => current.map((item) => item.id === link.id
      ? { ...item, status: "REVOKED", revokedAt: new Date().toISOString() }
      : item));
    setBusyId(link.id);
    setError(null);
    try {
      replaceLink(await revokeLinkedLearner(link.id));
      setNotice("Learning connection revoked.");
    } catch (revokeError) {
      setLinks(previous);
      setError(errorMessage(revokeError, "Could not revoke the connection. Your previous state was restored."));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6">
      <PageHeader
        eyebrow="Learning support"
        title="Learning connections"
        description="Invite someone to support your learning, or offer support to someone you know. Both people must agree, and either can revoke the connection at any time."
      />

      <Card className="space-y-4 p-4 sm:p-6">
        <div>
          <h2 className="text-lg font-semibold">Invite with a link</h2>
          <p className="mt-1 text-sm text-foreground/70">
            Create a single-use link when you do not have the other person&apos;s email. Opening it sends you a connection request that you must confirm.
          </p>
        </div>
        <form className="space-y-4" onSubmit={handleCreateInvitationLink}>
          <div className="grid gap-2 sm:grid-cols-2">
            <button type="button" onClick={() => setLinkCreatorRole("SUPPORTER")} aria-pressed={linkCreatorRole === "SUPPORTER"} className={`rounded-lg border p-3 text-left text-sm ${linkCreatorRole === "SUPPORTER" ? "border-primary bg-primary/10" : "border-border"}`}>
              <span className="font-medium">The link is for a learner</span>
              <span className="mt-1 block text-foreground/65">The person opening the link is the learner.</span>
            </button>
            <button type="button" onClick={() => setLinkCreatorRole("LEARNER")} aria-pressed={linkCreatorRole === "LEARNER"} className={`rounded-lg border p-3 text-left text-sm ${linkCreatorRole === "LEARNER" ? "border-primary bg-primary/10" : "border-border"}`}>
              <span className="font-medium">The link is for a supporter</span>
              <span className="mt-1 block text-foreground/65">The person opening the link is your supporter.</span>
            </button>
          </div>
          {linkCreatorRole === "LEARNER" ? (
            <div className="space-y-1.5 text-sm font-medium">
              <label className="block" htmlFor="invitation-link-birth-year">Your birth year</label>
              <BirthYearInput
                id="invitation-link-birth-year"
                value={linkBirthYear}
                onChange={setLinkBirthYear}
              />
              <span className="block text-xs font-normal text-foreground/60">
                Needed only if you have not recorded it before, so guardian consent cannot be bypassed through a link.
              </span>
            </div>
          ) : null}
          <Button type="submit" loading={creatingInvitationLink} loadingText="Creating link…">
            Create invitation link
          </Button>
        </form>

        {invitationLinkNotice ? (
          <p role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-100">
            {invitationLinkNotice}
          </p>
        ) : null}
        {invitationLinkError ? (
          <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
            {invitationLinkError}
          </p>
        ) : null}

        <div className="space-y-3" aria-label="Live invitation links">
          <h3 className="font-medium">Your live links</h3>
          {invitationLinksLoading ? <Skeleton className="h-16 w-full" /> : null}
          {!invitationLinksLoading && invitationLinks.length === 0 ? (
            <p className="text-sm text-foreground/65">No live invitation links.</p>
          ) : null}
          {invitationLinks.map((link) => (
            <div key={link.id} className="space-y-2 rounded-lg border border-border p-3">
              <p className="text-sm font-medium">
                {link.creatorRole === "SUPPORTER" ? "You will support the person who opens this" : "The person who opens this will support you"}
              </p>
              <p className="text-xs text-foreground/60">Expires {formatDate(link.expiresAt)}</p>
              <input className={INPUT_CLASSES} readOnly value={link.url} aria-label="Invitation link URL" />
              <div className="flex flex-wrap gap-2">
                <Button type="button" variant="outline" onClick={() => void handleCopyInvitationLink(link)}>Copy link</Button>
                <Button type="button" variant="destructiveOutline" loading={busyInvitationLinkId === link.id} onClick={() => void handleRevokeInvitationLink(link)}>Revoke link</Button>
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card className="space-y-4 p-4 sm:p-6">
        <div>
          <h2 className="text-lg font-semibold">Send an invitation</h2>
          <p className="mt-1 text-sm text-foreground/70">The other person must accept before the connection becomes active. They do not need a NoteLib account yet — if they do not have one, the invitation waits for them to sign up.</p>
        </div>
        <form ref={inviteFormRef} className="space-y-4" onSubmit={handleInvite} noValidate>
          <div className="grid gap-2 sm:grid-cols-2">
            <button type="button" onClick={() => setInviterRole("SUPPORTER")} aria-pressed={inviterRole === "SUPPORTER"} className={`rounded-lg border p-3 text-left text-sm ${inviterRole === "SUPPORTER" ? "border-primary bg-primary/10" : "border-border"}`}>
              <span className="font-medium">I will support them</span>
              <span className="mt-1 block text-foreground/65">Invite a learner you help.</span>
            </button>
            <button type="button" onClick={() => setInviterRole("LEARNER")} aria-pressed={inviterRole === "LEARNER"} className={`rounded-lg border p-3 text-left text-sm ${inviterRole === "LEARNER" ? "border-primary bg-primary/10" : "border-border"}`}>
              <span className="font-medium">They will support me</span>
              <span className="mt-1 block text-foreground/65">Invite a parent, tutor or mentor.</span>
            </button>
          </div>
          <label className="block space-y-1.5 text-sm font-medium">
            Their email
            <input
              ref={emailInputRef}
              className={INPUT_CLASSES}
              type="email"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
                setFieldErrors((previous) => ({ ...previous, email: null }));
              }}
              autoComplete="email"
              aria-invalid={fieldErrors.email ? true : undefined}
              aria-describedby={fieldErrors.email ? "invite-email-error" : undefined}
            />
            {fieldErrors.email ? (
              <p id="invite-email-error" role="alert" className="text-xs font-normal text-red-700 dark:text-red-300">
                {fieldErrors.email}
              </p>
            ) : null}
          </label>
          {inviterRole === "LEARNER" ? (
            <div className="space-y-1.5 text-sm font-medium">
              <label className="block" htmlFor="invite-birth-year">Your birth year</label>
              <BirthYearInput
                id="invite-birth-year"
                value={inviteBirthYear}
                inputRef={birthYearInputRef}
                invalid={Boolean(fieldErrors.birthYear)}
                describedBy={
                  fieldErrors.birthYear ? "invite-birth-year-error" : "invite-birth-year-help"
                }
                onChange={(next) => {
                  setInviteBirthYear(next);
                  // Live, but only once the year is complete — flagging "19" mid-typing would be noise.
                  // Waiting for submit was the gap: a plainly impossible year sat there looking accepted.
                  setFieldErrors((previous) => ({
                    ...previous,
                    birthYear: next.length === 4 ? birthYearRangeError(next) : null,
                  }));
                }}
              />
              {fieldErrors.birthYear ? (
                <p id="invite-birth-year-error" role="alert" className="text-xs font-normal text-red-700 dark:text-red-300">
                  {fieldErrors.birthYear}
                </p>
              ) : null}
              <p id="invite-birth-year-help" className="text-xs font-normal text-foreground/60">
                Needed now so the person you invite can accept. If you are under the guardian-consent
                age, a guardian confirms before the connection becomes active.
              </p>
            </div>
          ) : null}
          <Button type="submit" loading={inviting} loadingText="Sending invitation…">Send invitation</Button>
        </form>
      </Card>

      {/*
        ⚠️ An outgoing LEARNER invitation or live invitation link counts, not just an existing relationship. `invite` writes
        the write-once account-global birth year and creates NO relationship row, so gating on
        `links` alone hid this card for the entire life of an unaccepted invitation — up to the full
        TTL, or forever if it is never accepted. That is precisely the flow v0.90.0 added, and the
        release documents the learner-only correction path as the mitigation for it.
      */}
      {(links.some((link) => link.callerRole === "LEARNER" && !link.birthYearRequired)
        || invitations.some((invitation) => !invitation.incoming && invitation.inviterRole === "LEARNER")
        || invitationLinks.some((link) => link.creatorRole === "LEARNER")) ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <div>
            <h2 className="text-lg font-semibold">Correct your birth year</h2>
            <p className="mt-1 text-sm text-foreground/70">This is your account-level birth year used for guardian consent across all learning connections.</p>
          </div>
          <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={handleBirthYearCorrection} noValidate>
            <div className="flex-1 space-y-1.5 text-sm font-medium">
              <label className="block" htmlFor="corrected-birth-year">Corrected birth year</label>
              <BirthYearInput
                id="corrected-birth-year"
                value={correctedBirthYear}
                invalid={Boolean(correctionYearError)}
                describedBy={correctionYearError ? "corrected-birth-year-error" : undefined}
                onChange={(next) => {
                  setCorrectedBirthYear(next);
                  setCorrectionWarningCount(null);
                  setCorrectionYearError(next.length === 4 ? birthYearRangeError(next) : null);
                }}
              />
              {correctionYearError ? (
                <p id="corrected-birth-year-error" role="alert" className="text-xs font-normal text-red-700 dark:text-red-300">
                  {correctionYearError}
                </p>
              ) : null}
            </div>
            <Button type="submit" variant="outline" loading={correctingBirthYear}>Review correction</Button>
          </form>
          {correctionWarningCount !== null ? (
            <div role="alertdialog" aria-labelledby="birth-year-warning-title" className="space-y-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-100">
              <p id="birth-year-warning-title" className="font-medium">
                {correctionWarningCount} connection(s) will pause until a guardian confirms.
              </p>
              <p>Those supporters will lose progress access immediately. Connections with guardian consent already recorded will stay active.</p>
              <div className="flex flex-wrap gap-2">
                <Button type="button" onClick={() => void applyBirthYearCorrection()} loading={correctingBirthYear}>Apply correction</Button>
                <Button type="button" variant="outline" onClick={() => setCorrectionWarningCount(null)} disabled={correctingBirthYear}>Cancel</Button>
              </div>
            </div>
          ) : null}
        </Card>
      ) : null}

      {notice ? <p role="status" className="rounded-lg border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-100">{notice}</p> : null}
      {error ? <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">{error}</p> : null}

      {invitationsLoading ? <Skeleton className="h-20 w-full" /> : null}
      {invitationListError ? (
        <p role="alert" className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
          {invitationListError}
        </p>
      ) : null}

      {invitations.length > 0 ? (
        <section className="space-y-3" aria-labelledby="invitations-heading">
          <h2 id="invitations-heading" className="text-lg font-semibold">Pending invitations</h2>
          <ul className="space-y-3">
            {invitations.map((invitation) => (
              <li
                key={invitation.id}
                className={`rounded-xl border p-4 ${invitation.expired
                  ? "border-amber-300 bg-amber-50/70 dark:border-amber-800 dark:bg-amber-950/20"
                  : "border-border"}`}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <p className="text-sm font-medium text-foreground">
                  {invitation.incoming
                    ? `${invitation.inviterName ?? "Someone"} invited you`
                    : `You invited ${invitation.invitedEmail}`}
                  </p>
                  <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${invitation.expired
                    ? "bg-amber-200 text-amber-950 dark:bg-amber-900 dark:text-amber-100"
                    : "bg-muted text-foreground/75"}`}
                  >
                    {invitation.expired ? "Expired" : "Pending"}
                  </span>
                </div>
                <p className="mt-1 text-xs text-foreground/60">
                  {invitation.expired
                    ? `Expired on ${formatDate(invitation.expiresAt)}. Invite them again to send a new email and reopen the invitation.`
                    : invitation.incoming
                    ? (invitation.inviterRole === "SUPPORTER"
                        ? "They would support your learning."
                        : "They are asking you to support their learning.")
                    : "Waiting for them to accept."}
                </p>
                {!invitation.expired ? (
                  <p className="mt-1 text-xs text-foreground/55">Expires {formatDate(invitation.expiresAt)}</p>
                ) : null}
                {invitation.incoming && !invitation.expired ? (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <label className="text-xs text-foreground/70" htmlFor={`birth-year-${invitation.id}`}>
                      Your birth year
                    </label>
                    <BirthYearInput
                      id={`birth-year-${invitation.id}`}
                      value={birthYears[invitation.id] ?? ""}
                      onChange={(next) => setBirthYears((current) => ({
                        ...current, [invitation.id]: next,
                      }))}
                    />
                    <div className="flex items-center gap-2 text-xs text-foreground/70">
                      <Checkbox
                        id={`consent-invitation-${invitation.id}`}
                        checked={consentChecked[invitation.id] === true}
                        onChange={(checked) => setConsentChecked((current) => ({
                          ...current, [invitation.id]: checked,
                        }))}
                        ariaLabel="Confirm guardian consent attestation"
                      />
                      <label htmlFor={`consent-invitation-${invitation.id}`}>
                        A guardian confirms this connection
                      </label>
                    </div>
                    <Button
                      type="button"
                      disabled={busyId === invitation.id}
                      onClick={() => void handleAcceptInvitation(invitation)}
                    >
                      Accept
                    </Button>
                  </div>
                ) : null}
                <div className="mt-3 flex flex-wrap gap-2">
                  {invitation.expired && !invitation.incoming ? (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => handleInviteAgain(invitation)}
                    >
                      Invite again
                    </Button>
                  ) : null}
                  <Button
                    type="button"
                    variant={invitation.expired ? "destructiveOutline" : "outline"}
                    disabled={busyId === invitation.id}
                    onClick={() => void handleWithdrawInvitation(invitation)}
                  >
                    {invitation.incoming ? "Decline" : "Withdraw"}
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <section className="space-y-3" aria-labelledby="connections-heading">
        <h2 id="connections-heading" className="text-lg font-semibold">Your invitations and connections</h2>
        {loading ? <Card className="space-y-3 p-4"><Skeleton className="h-5 w-40" /><Skeleton className="h-16 w-full" /></Card> : null}
        {!loading && links.length === 0 ? <Card className="p-5 text-sm text-foreground/70">No invitations or connections yet.</Card> : null}
        {links.map((link) => {
          const pending = link.status === "PENDING";
          const statusDescription = describeSupportedLearnerStatus(link);
          const canAccept = pending && link.incomingInvitation;
          const learnerCanSupplyYear = pending && link.callerRole === "LEARNER" && link.birthYearRequired;
          const supporterCanConsent = pending && link.callerRole === "SUPPORTER" && link.guardianConsentRequired && !link.guardianConsentRecorded;
          return (
            <Card key={link.id} className="space-y-4 p-4 sm:p-5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="font-semibold">{link.counterpartyDisplayName}</h3>
                  {link.counterpartyEmail ? <p className="text-sm text-foreground/65">{link.counterpartyEmail}</p> : null}
                  <p className="mt-1 text-xs text-foreground/55">You are the {link.callerRole === "SUPPORTER" ? "supporter" : "learner"} · Invited {formatDate(link.createdAt)}</p>
                </div>
                <span className="w-fit rounded-full bg-muted px-2.5 py-1 text-xs font-medium">{link.status.toLowerCase()}</span>
              </div>

              {learnerCanSupplyYear ? (
                <div className="space-y-2 rounded-lg border border-border p-3">
                  <div className="space-y-1.5 text-sm font-medium">
                    <label className="block" htmlFor={`birth-year-link-${link.id}`}>Your birth year</label>
                    <BirthYearInput
                      id={`birth-year-link-${link.id}`}
                      value={birthYears[link.id] ?? ""}
                      onChange={(next) => setBirthYears((current) => ({ ...current, [link.id]: next }))}
                    />
                  </div>
                  {!canAccept ? <Button type="button" variant="outline" onClick={() => void handleBirthYear(link)} loading={busyId === link.id}>Save birth year</Button> : null}
                  <p className="text-xs text-foreground/60">We collect the year only, for the consent decision on this connection. It is not part of signup or your public profile.</p>
                </div>
              ) : null}

              {supporterCanConsent ? (
                <div className="space-y-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-100">
                  <p className="font-medium">Guardian consent is required before this connection can be accepted.</p>
                  <div className="flex items-start gap-2">
                    <Checkbox id={`consent-${link.id}`} checked={consentChecked[link.id] === true} onChange={(checked) => setConsentChecked((current) => ({ ...current, [link.id]: checked }))} ariaLabel="Confirm guardian consent attestation" />
                    {/* PLACEHOLDER FOR COUNSEL: replace this attestation before relying on it as legal copy. */}
                    <label htmlFor={`consent-${link.id}`}>Consent wording placeholder pending counsel: I attest that I am authorized to give guardian consent for this learning connection.</label>
                  </div>
                  {!canAccept ? <Button type="button" onClick={() => void handleConsent(link)} loading={busyId === link.id}>Record guardian consent</Button> : null}
                </div>
              ) : null}

              {pending && link.birthYearRequired && link.callerRole === "SUPPORTER" ? <p className="text-sm text-foreground/70">{LINKED_LEARNER_STATUS_COPY.waitingForLearnerBirthYear}</p> : null}
              {pending && link.guardianConsentRequired && !link.guardianConsentRecorded && link.callerRole === "LEARNER" ? <p className="text-sm text-foreground/70">{LINKED_LEARNER_STATUS_COPY.pausedForConsentLearnerView}</p> : null}
              {pending && link.guardianConsentRequired && !link.guardianConsentRecorded && link.callerRole === "SUPPORTER" ? <p className="text-sm text-foreground/70">{LINKED_LEARNER_STATUS_COPY.pausedForConsentSupporterView}</p> : null}
              {pending && link.guardianConsentRequired && link.guardianConsentRecorded ? <p className="text-sm text-foreground/70">{LINKED_LEARNER_STATUS_COPY.consentRecorded}</p> : null}
              {link.status === "EXPIRED" ? (
                <div className="rounded-lg border border-border bg-muted/35 p-3 text-sm text-foreground/70">
                  <p className="font-medium text-foreground">{statusDescription.headline}</p>
                  <p className="mt-1">{statusDescription.detail}</p>
                </div>
              ) : null}

              {link.status === "ACCEPTED" || link.status === "PENDING" ? (
                <SharingPanel
                  link={link}
                  // ⚠️ Field-level merge, not `{ ...link }` — `link` is the snapshot from the render that
                  // created this handler, so writing the whole object back would revert any field a
                  // concurrent loadLinks() had refreshed (status, the other direction's grant, consent).
                  onActivityGrantUpdated={(granted) => updateLinkFields(link.id, { activitySharedByMe: granted })}
                  onProgressGrantUpdated={(granted) => updateLinkFields(link.id, { progressSharedByMe: granted })}
                  onAccessEnded={loadLinks}
                  onFailure={(message) => setError(message)}
                />
              ) : null}

              <div className="flex flex-wrap gap-2">
                {link.progressSharedWithMe ? (
                  <ResponsiveActionLink href={`/linked-learners/${link.id}/progress`} action="progress" label="View progress" />
                ) : null}
                {canAccept ? <Button type="button" onClick={() => void handleAccept(link)} loading={busyId === link.id} disabled={(link.birthYearRequired && (link.callerRole !== "LEARNER" || !birthYears[link.id]?.trim())) || (supporterCanConsent && consentChecked[link.id] !== true)}>Accept invitation</Button> : null}
                {link.status === "ACCEPTED" || link.status === "PENDING" ? <Button type="button" variant="destructiveOutline" onClick={() => void handleRevoke(link)} loading={busyId === link.id}>Revoke</Button> : null}
              </div>
              {link.acceptedAt ? <p className="text-xs text-foreground/55">Accepted {formatDate(link.acceptedAt)}</p> : null}
              {link.revokedAt ? <p className="text-xs text-foreground/55">Revoked {formatDate(link.revokedAt)}</p> : null}
              {/*
                ⚠️ A request now has a hard deadline, so it must be visible — otherwise this repeats
                the defect v0.94.0 item 3 fixed for invitations, where an expiry nobody could see
                made requests die in silence. Sharpest on the consent path: a supporter who must
                record guardian consent otherwise has no way to learn the window exists.
                ⚠️ Gated on PENDING because a null expiresAt means "not on the clock" (acceptance
                clears it; a consent pause leaves it clear), not "unknown".
              */}
              {link.status === "PENDING" && link.expiresAt
                ? <p className="text-xs text-foreground/55">Expires {formatDate(link.expiresAt)} if it is not confirmed</p>
                : null}
            </Card>
          );
        })}
      </section>
    </main>
  );
}
