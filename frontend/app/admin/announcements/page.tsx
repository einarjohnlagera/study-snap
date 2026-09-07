"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ToastMessage } from "@/components/ui/toast-message";
import {
  ApiRequestError,
  createAnnouncement,
  endAnnouncement,
  listAnnouncements,
  publishAnnouncement,
  updateAnnouncement,
  type AnnouncementAudience,
  type AnnouncementResponse,
  type UpsertAnnouncementRequest,
} from "@/lib/api";
import { requireAdminUser } from "@/lib/route-guards";

const AUDIENCES: { value: AnnouncementAudience; label: string; valueOptions: string[] }[] = [
  { value: "EVERYONE", label: "Everyone", valueOptions: [] },
  {
    value: "PROFILE_TYPE",
    label: "Profile type",
    valueOptions: ["STUDENT", "BOARD_EXAM", "TEACHER", "PARENT", "PROFESSIONAL"],
  },
  { value: "PLAN_TYPE", label: "Plan", valueOptions: ["FREE", "PLUS", "PRO"] },
];

type FormState = {
  title: string;
  body: string;
  ctaLabel: string;
  ctaPath: string;
  audience: AnnouncementAudience;
  audienceValue: string;
  expiresAt: string;
};

const EMPTY_FORM: FormState = {
  title: "",
  body: "",
  ctaLabel: "",
  ctaPath: "",
  audience: "EVERYONE",
  audienceValue: "",
  expiresAt: "",
};

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("en-US", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function describeAudience(announcement: AnnouncementResponse): string {
  if (announcement.audience === "EVERYONE") {
    return "Everyone";
  }
  const label = AUDIENCES.find((option) => option.value === announcement.audience)?.label ?? announcement.audience;
  return `${label}: ${announcement.audienceValue ?? "—"}`;
}

function describeStatus(announcement: AnnouncementResponse): string {
  if (announcement.status === "PUBLISHED" && announcement.expired) {
    // ⚠️ Expiry is evaluated on READ, so an expired announcement stops appearing in learner inboxes
    // the moment its timestamp passes — no job runs, and the row keeps its PUBLISHED status.
    return "Expired";
  }
  if (announcement.status === "DRAFT") return "Draft";
  if (announcement.status === "PUBLISHED") return "Published";
  return "Ended";
}

function toFormState(announcement: AnnouncementResponse): FormState {
  return {
    title: announcement.title,
    body: announcement.body,
    ctaLabel: announcement.ctaLabel ?? "",
    ctaPath: announcement.ctaPath ?? "",
    audience: announcement.audience,
    audienceValue: announcement.audienceValue ?? "",
    expiresAt: announcement.expiresAt ? announcement.expiresAt.slice(0, 16) : "",
  };
}

function toRequest(form: FormState): UpsertAnnouncementRequest {
  return {
    title: form.title.trim(),
    body: form.body.trim(),
    ctaLabel: form.ctaLabel.trim() === "" ? null : form.ctaLabel.trim(),
    ctaPath: form.ctaPath.trim() === "" ? null : form.ctaPath.trim(),
    audience: form.audience,
    audienceValue: form.audience === "EVERYONE" || form.audienceValue === "" ? null : form.audienceValue,
    expiresAt: form.expiresAt === "" ? null : new Date(form.expiresAt).toISOString(),
  };
}

export default function AdminAnnouncementsPage() {
  const router = useRouter();
  const [announcements, setAnnouncements] = useState<AnnouncementResponse[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [publishTarget, setPublishTarget] = useState<AnnouncementResponse | null>(null);
  const [endTarget, setEndTarget] = useState<AnnouncementResponse | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const loadAnnouncements = useCallback(async () => {
    if (!requireAdminUser(router)) {
      return;
    }
    setLoadError(null);
    try {
      setAnnouncements(await listAnnouncements());
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 403) {
        router.replace("/dashboard");
        return;
      }
      setLoadError(err instanceof Error ? err.message : "Could not load announcements.");
    }
  }, [router]);

  useEffect(() => {
    void loadAnnouncements();
  }, [loadAnnouncements]);

  useEffect(() => {
    if (!successMessage) {
      return;
    }
    const id = globalThis.setTimeout(() => setSuccessMessage(null), 5000);
    return () => {
      globalThis.clearTimeout(id);
    };
  }, [successMessage]);

  const selectedAudience = AUDIENCES.find((option) => option.value === form.audience) ?? AUDIENCES[0];

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setFormError(null);
  };

  const handleSave = async () => {
    setSaving(true);
    setFormError(null);
    try {
      if (editingId) {
        await updateAnnouncement(editingId, toRequest(form));
        setSuccessMessage("Draft updated.");
      } else {
        await createAnnouncement(toRequest(form));
        setSuccessMessage("Draft created.");
      }
      resetForm();
      void loadAnnouncements();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Could not save the announcement.");
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    if (!publishTarget) {
      return;
    }
    setActing(true);
    setActionError(null);
    try {
      const result = await publishAnnouncement(publishTarget.id);
      setPublishTarget(null);
      setSuccessMessage(
        `Published to ${result.delivered} of ${result.recipientCount} ${result.recipientCount === 1 ? "person" : "people"}` +
          (result.skipped > 0 ? ` — ${result.skipped} failed, press Publish again to retry those.` : ".") +
          (result.delivered === 0 && result.recipientCount > 0
            ? " Nobody actually received it — check the logs before assuming it went out."
            : ""),
      );
      void loadAnnouncements();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Could not publish the announcement.");
    } finally {
      setActing(false);
    }
  };

  const handleEnd = async () => {
    if (!endTarget) {
      return;
    }
    setActing(true);
    setActionError(null);
    try {
      await endAnnouncement(endTarget.id);
      setEndTarget(null);
      setSuccessMessage("Announcement ended. It stops appearing in inboxes immediately.");
      void loadAnnouncements();
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Could not end the announcement.");
    } finally {
      setActing(false);
    }
  };

  const canSave = form.title.trim() !== "" && form.body.trim() !== "" && !saving;

  return (
    <div className="mx-auto w-full max-w-7xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      {successMessage ? <ToastMessage message={successMessage} tone="success" /> : null}

      <header className="space-y-2">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-sm text-foreground/55 hover:text-foreground/80">
            ← Admin
          </Link>
        </div>
        <h1 className="text-3xl font-semibold text-foreground">What&apos;s New</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          Publish an announcement into every targeted learner&apos;s notification inbox. Announcements never add
          to the bell&apos;s unread number — they appear in the inbox and wait to be read.
        </p>
      </header>

      <Card className="space-y-4 p-5">
        <div className="space-y-1">
          <h2 className="text-base font-semibold text-foreground">
            {editingId ? "Edit draft" : "New announcement"}
          </h2>
          {/* ⚠️ Immutability-after-publish is stated in the form, not discovered when Edit disappears. */}
          <p className="text-sm text-foreground/65">
            Drafts can be edited freely. <strong className="font-medium text-foreground">Once published, the
            title, body and link can no longer be changed</strong> — delivered notifications keep a copy of the
            text as it was at publish time. To correct a published announcement, end it and publish a
            replacement.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="space-y-1 sm:col-span-2">
            <span className="text-sm font-medium text-foreground">Title</span>
            <input
              type="text"
              maxLength={255}
              value={form.title}
              onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </label>

          <label className="space-y-1 sm:col-span-2">
            <span className="text-sm font-medium text-foreground">Body</span>
            <textarea
              rows={3}
              maxLength={1000}
              value={form.body}
              onChange={(event) => setForm((current) => ({ ...current, body: event.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </label>

          <label className="space-y-1">
            <span className="text-sm font-medium text-foreground">Link label</span>
            <input
              type="text"
              maxLength={64}
              value={form.ctaLabel}
              onChange={(event) => setForm((current) => ({ ...current, ctaLabel: event.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
          </label>

          <label className="space-y-1">
            <span className="text-sm font-medium text-foreground">Link path</span>
            <input
              type="text"
              maxLength={512}
              placeholder="/dashboard"
              value={form.ctaPath}
              onChange={(event) => setForm((current) => ({ ...current, ctaPath: event.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
            <span className="block text-xs text-foreground/55">
              A path inside NoteLib, starting with &ldquo;/&rdquo;. External links are rejected.
            </span>
          </label>

          <label className="space-y-1">
            <span className="text-sm font-medium text-foreground">Audience</span>
            <select
              value={form.audience}
              onChange={(event) => setForm((current) => ({
                ...current,
                audience: event.target.value as AnnouncementAudience,
                audienceValue: "",
              }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            >
              {AUDIENCES.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
            <span className="block text-xs text-foreground/55">
              Targeting decides who sees the message. It never changes what anyone can do in the product.
            </span>
          </label>

          {selectedAudience.valueOptions.length > 0 ? (
            <label className="space-y-1">
              <span className="text-sm font-medium text-foreground">{selectedAudience.label} value</span>
              <select
                value={form.audienceValue}
                onChange={(event) => setForm((current) => ({ ...current, audienceValue: event.target.value }))}
                className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
              >
                <option value="">Select…</option>
                {selectedAudience.valueOptions.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </label>
          ) : null}

          <label className="space-y-1">
            <span className="text-sm font-medium text-foreground">Expires (optional)</span>
            <input
              type="datetime-local"
              value={form.expiresAt}
              onChange={(event) => setForm((current) => ({ ...current, expiresAt: event.target.value }))}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm"
            />
            <span className="block text-xs text-foreground/55">
              After this time it stops appearing in inboxes immediately.
            </span>
          </label>
        </div>

        {formError ? <p className="text-sm text-red-600 dark:text-red-400">{formError}</p> : null}

        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          {editingId ? (
            <Button type="button" variant="outline" onClick={resetForm} disabled={saving}>
              Cancel
            </Button>
          ) : null}
          <Button type="button" onClick={() => void handleSave()} disabled={!canSave}>
            {saving ? "Saving…" : editingId ? "Save draft" : "Create draft"}
          </Button>
        </div>
      </Card>

      <section className="space-y-3">
        <h2 className="text-base font-semibold text-foreground">Announcements</h2>
        {loadError ? <p className="text-sm text-red-600 dark:text-red-400">{loadError}</p> : null}
        {actionError ? <p className="text-sm text-red-600 dark:text-red-400">{actionError}</p> : null}
        {!loadError && announcements.length === 0 ? (
          <Card className="p-5 text-sm text-foreground/65">No announcements yet.</Card>
        ) : null}
        {announcements.map((announcement) => (
          <Card key={announcement.id} className="space-y-3 p-5">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
              <div className="min-w-0 space-y-1">
                <p className="font-semibold text-foreground">{announcement.title}</p>
                <p className="text-sm text-foreground/65">{announcement.body}</p>
                <p className="text-xs text-foreground/55">
                  {describeStatus(announcement)} · {describeAudience(announcement)}
                  {announcement.publishedAt ? ` · published ${formatDateTime(announcement.publishedAt)}` : ""}
                  {announcement.expiresAt ? ` · expires ${formatDateTime(announcement.expiresAt)}` : ""}
                </p>
                {announcement.ctaPath ? (
                  <p className="text-xs text-foreground/55">
                    {announcement.ctaLabel} → {announcement.ctaPath}
                  </p>
                ) : null}
              </div>
              <div className="flex shrink-0 flex-wrap gap-2">
                {announcement.editable ? (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => {
                      setEditingId(announcement.id);
                      setForm(toFormState(announcement));
                      setFormError(null);
                    }}
                  >
                    Edit
                  </Button>
                ) : null}
                {announcement.status !== "ENDED" ? (
                  <Button size="sm" onClick={() => { setActionError(null); setPublishTarget(announcement); }}>
                    {announcement.status === "DRAFT" ? "Publish" : "Retry fan-out"}
                  </Button>
                ) : null}
                {announcement.status === "PUBLISHED" ? (
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => { setActionError(null); setEndTarget(announcement); }}
                  >
                    End
                  </Button>
                ) : null}
              </div>
            </div>
          </Card>
        ))}
      </section>

      <AppModal
        isOpen={publishTarget !== null}
        title={publishTarget?.status === "DRAFT" ? "Publish announcement" : "Retry fan-out"}
        description={
          publishTarget?.status === "DRAFT"
            ? "This delivers the announcement to every targeted learner's inbox right now, and the title, body and link can no longer be changed afterwards. This cannot be undone — to correct it later you would end it and publish a replacement."
            // ⚠️ This used to read "This re-runs delivery for anyone the first attempt missed. Nobody
            // receives it twice." The second sentence is true — the unique index dedupes — but the first
            // was incomplete, and this is the copy an admin reads immediately before firing an
            // irreversible action. fanOut re-resolves the audience at call time, so anyone who joined the
            // audience since the first publish receives it now. It is a top-up, not only a retry.
            : "This re-runs delivery for anyone the first attempt missed, and also delivers to anyone who has joined this audience since — new signups, or people whose profile or plan now matches. Nobody receives it twice."
        }
        onClose={() => { if (!acting) { setPublishTarget(null); } }}
        panelClassName="max-w-[520px]"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setPublishTarget(null)} disabled={acting}>
              Cancel
            </Button>
            <Button type="button" onClick={() => void handlePublish()} disabled={acting}>
              {acting ? "Publishing…" : publishTarget?.status === "DRAFT" ? "Publish now" : "Retry now"}
            </Button>
          </div>
        )}
      >
        {actionError ? <p className="mt-2 text-sm text-red-600 dark:text-red-400">{actionError}</p> : null}
      </AppModal>

      <AppModal
        isOpen={endTarget !== null}
        title="End announcement"
        description="It stops appearing in learner inboxes immediately. Notifications already delivered are not deleted — they simply stop showing."
        onClose={() => { if (!acting) { setEndTarget(null); } }}
        panelClassName="max-w-[520px]"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={() => setEndTarget(null)} disabled={acting}>
              Cancel
            </Button>
            <Button type="button" onClick={() => void handleEnd()} disabled={acting}>
              {acting ? "Ending…" : "End now"}
            </Button>
          </div>
        )}
      >
        {actionError ? <p className="mt-2 text-sm text-red-600 dark:text-red-400">{actionError}</p> : null}
      </AppModal>
    </div>
  );
}
