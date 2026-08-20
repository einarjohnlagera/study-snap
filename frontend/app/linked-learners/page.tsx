"use client";

import { useCallback, useEffect, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { ResponsiveActionLink } from "@/components/ui/action-button";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Skeleton } from "@/components/ui/skeleton";
import {
  acceptLinkedLearner,
  correctLinkedLearnerBirthYear,
  getLinkedLearners,
  inviteLinkedLearner,
  previewLinkedLearnerBirthYearCorrection,
  recordLinkedLearnerBirthYear,
  recordLinkedLearnerGuardianConsent,
  revokeLinkedLearner,
  type LinkedLearnerResponse,
  type LinkedLearnerSide,
} from "@/lib/api";

const INPUT_CLASSES = "h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary";

function formatDate(value: string | null) {
  if (!value) return null;
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium" }).format(new Date(value));
}

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

export default function LinkedLearnersPage() {
  const [links, setLinks] = useState<LinkedLearnerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [email, setEmail] = useState("");
  const [inviterRole, setInviterRole] = useState<LinkedLearnerSide>("SUPPORTER");
  const [birthYears, setBirthYears] = useState<Record<string, string>>({});
  const [correctedBirthYear, setCorrectedBirthYear] = useState("");
  const [correctionWarningCount, setCorrectionWarningCount] = useState<number | null>(null);
  const [consentChecked, setConsentChecked] = useState<Record<string, boolean>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [inviting, setInviting] = useState(false);
  const [correctingBirthYear, setCorrectingBirthYear] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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

  const replaceLink = (updated: LinkedLearnerResponse) => {
    setLinks((current) => current.map((link) => link.id === updated.id ? updated : link));
  };

  const handleInvite = async (event: React.FormEvent) => {
    event.preventDefault();
    setInviting(true);
    setError(null);
    setNotice(null);
    try {
      const response = await inviteLinkedLearner(email, inviterRole);
      setNotice(response.message);
      setEmail("");
      await loadLinks();
    } catch (inviteError) {
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
    if (!correctedBirthYear.trim()) {
      setError("Enter your corrected birth year.");
      return;
    }
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
          <h2 className="text-lg font-semibold">Send an invitation</h2>
          <p className="mt-1 text-sm text-foreground/70">The other person needs their own NoteLib account and must accept before the connection becomes active.</p>
        </div>
        <form className="space-y-4" onSubmit={handleInvite}>
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
            <input className={INPUT_CLASSES} type="email" value={email} onChange={(event) => setEmail(event.target.value)} required autoComplete="email" />
          </label>
          <Button type="submit" loading={inviting} loadingText="Sending invitation…">Send invitation</Button>
        </form>
      </Card>

      {links.some((link) => link.callerRole === "LEARNER" && !link.birthYearRequired) ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <div>
            <h2 className="text-lg font-semibold">Correct your birth year</h2>
            <p className="mt-1 text-sm text-foreground/70">This is your account-level birth year used for guardian consent across all learning connections.</p>
          </div>
          <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={handleBirthYearCorrection}>
            <label className="block flex-1 space-y-1.5 text-sm font-medium">
              Corrected birth year
              <input className={INPUT_CLASSES} type="number" min="1900" max="9999" value={correctedBirthYear} onChange={(event) => { setCorrectedBirthYear(event.target.value); setCorrectionWarningCount(null); }} required />
            </label>
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

      <section className="space-y-3" aria-labelledby="connections-heading">
        <h2 id="connections-heading" className="text-lg font-semibold">Your invitations and connections</h2>
        {loading ? <Card className="space-y-3 p-4"><Skeleton className="h-5 w-40" /><Skeleton className="h-16 w-full" /></Card> : null}
        {!loading && links.length === 0 ? <Card className="p-5 text-sm text-foreground/70">No invitations or connections yet.</Card> : null}
        {links.map((link) => {
          const pending = link.status === "PENDING";
          const canAccept = pending && link.incomingInvitation;
          const learnerCanSupplyYear = pending && link.callerRole === "LEARNER" && link.birthYearRequired;
          const supporterCanConsent = pending && link.callerRole === "SUPPORTER" && link.guardianConsentRequired && !link.guardianConsentRecorded;
          return (
            <Card key={link.id} className="space-y-4 p-4 sm:p-5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <h3 className="font-semibold">{link.counterpartyDisplayName}</h3>
                  <p className="text-sm text-foreground/65">{link.counterpartyEmail}</p>
                  <p className="mt-1 text-xs text-foreground/55">You are the {link.callerRole === "SUPPORTER" ? "supporter" : "learner"} · Invited {formatDate(link.createdAt)}</p>
                </div>
                <span className="w-fit rounded-full bg-muted px-2.5 py-1 text-xs font-medium">{link.status.toLowerCase()}</span>
              </div>

              {learnerCanSupplyYear ? (
                <div className="space-y-2 rounded-lg border border-border p-3">
                  <label className="block space-y-1.5 text-sm font-medium">
                    Your birth year
                    <input className={INPUT_CLASSES} type="number" min="1900" max={new Date().getFullYear()} value={birthYears[link.id] ?? ""} onChange={(event) => setBirthYears((current) => ({ ...current, [link.id]: event.target.value }))} />
                  </label>
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

              {pending && link.birthYearRequired && link.callerRole === "SUPPORTER" ? <p className="text-sm text-foreground/70">Waiting for the learner to record their birth year.</p> : null}
              {pending && link.guardianConsentRequired && !link.guardianConsentRecorded && link.callerRole === "LEARNER" ? <p className="text-sm text-foreground/70">This connection is paused until the supporter records guardian consent.</p> : null}
              {pending && link.guardianConsentRequired && !link.guardianConsentRecorded && link.callerRole === "SUPPORTER" ? <p className="text-sm text-foreground/70">Your progress access is paused because guardian consent is required. Record consent above to unblock the connection.</p> : null}
              {pending && link.guardianConsentRequired && link.guardianConsentRecorded ? <p className="text-sm text-foreground/70">Guardian consent has been recorded.</p> : null}

              <div className="flex flex-wrap gap-2">
                {link.status === "ACCEPTED" && link.callerRole === "SUPPORTER" ? (
                  <ResponsiveActionLink href={`/linked-learners/${link.id}/progress`} action="progress" label="View progress" />
                ) : null}
                {canAccept ? <Button type="button" onClick={() => void handleAccept(link)} loading={busyId === link.id} disabled={(link.birthYearRequired && (link.callerRole !== "LEARNER" || !birthYears[link.id]?.trim())) || (supporterCanConsent && consentChecked[link.id] !== true)}>Accept invitation</Button> : null}
                {link.status !== "REVOKED" ? <Button type="button" variant="destructiveOutline" onClick={() => void handleRevoke(link)} loading={busyId === link.id}>Revoke</Button> : null}
              </div>
              {link.acceptedAt ? <p className="text-xs text-foreground/55">Accepted {formatDate(link.acceptedAt)}</p> : null}
              {link.revokedAt ? <p className="text-xs text-foreground/55">Revoked {formatDate(link.revokedAt)}</p> : null}
            </Card>
          );
        })}
      </section>
    </main>
  );
}
