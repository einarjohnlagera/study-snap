"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { getAuthUser } from "@/lib/auth";
import {
  listSubjects,
  listPublicNotes,
  type NoteListItemResponse,
} from "@/lib/api";
import { resolvePublicNoteAuthorMeta } from "@/lib/public-note-author";
import { buildPublicLibraryNotePath } from "@/lib/public-note-path";
import { normalizeSubject } from "@/lib/subjects";
import { SubjectBadge } from "./subject-badge";

const ALL_SUBJECTS = "__ALL_SUBJECTS__";

function normalizeTags(tags: string[] | null | undefined): string[] {
  if (!Array.isArray(tags)) {
    return [];
  }
  return tags
    .map((tag) => tag?.trim())
    .filter((tag): tag is string => Boolean(tag && tag.length > 0));
}

function toPreview(contentPreview: string, maxLength = 160) {
  const clean = contentPreview.trim();
  if (clean.length <= maxLength) {
    return clean;
  }
  return `${clean.slice(0, maxLength - 3)}...`;
}

function resolveAuthorBadge(
  item: Pick<NoteListItemResponse, "ownerUserId" | "authorDisplayName" | "isCurrentUser" | "isOfficialAuthor">,
  currentUserId: string | null,
): { label: string; className: string; showOfficialBadge: boolean } {
  const authorMeta = resolvePublicNoteAuthorMeta({
    ownerUserId: item.ownerUserId,
    currentUserId,
    authorDisplayName: item.authorDisplayName,
    isCurrentUser: item.isCurrentUser,
    isOfficialAuthor: item.isOfficialAuthor,
  });

  if (authorMeta.label === "By You") {
    return {
      label: authorMeta.label,
      className: "border-emerald-500/35 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300",
      showOfficialBadge: false,
    };
  }
  if (authorMeta.showOfficialBadge) {
    return {
      label: authorMeta.label,
      className: "border-blue-500/35 bg-blue-500/10 text-blue-700 dark:text-blue-300",
      showOfficialBadge: true,
    };
  }
  return {
    label: authorMeta.label,
    className: "border-border bg-muted/40 text-foreground/70",
    showOfficialBadge: false,
  };
}

export function PublicLibraryPageClient() {
  const router = useRouter();
  const [currentUserId, setCurrentUserId] = useState<string | null>(() => getAuthUser()?.id ?? null);
  const [items, setItems] = useState<NoteListItemResponse[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedSubject, setSelectedSubject] = useState<string>(ALL_SUBJECTS);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [tagSearchQuery, setTagSearchQuery] = useState("");
  const [tagFilterOpen, setTagFilterOpen] = useState(false);
  const [subjectSuggestions, setSubjectSuggestions] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadNotes = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [notesResult, subjectsResult] = await Promise.allSettled([
        listPublicNotes(),
        listSubjects("public"),
      ]);
      if (notesResult.status !== "fulfilled") {
        throw notesResult.reason;
      }
      setItems(notesResult.value);
      setSubjectSuggestions(subjectsResult.status === "fulfilled" ? subjectsResult.value : []);
    } catch (loadError) {
      const message = loadError instanceof Error ? loadError.message : "Could not load public notes.";
      setError(message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadNotes();
  }, [loadNotes]);

  useEffect(() => {
    const syncAuth = () => {
      setCurrentUserId(getAuthUser()?.id ?? null);
    };

    syncAuth();
    globalThis.addEventListener("studysnap-auth-change", syncAuth);
    return () => {
      globalThis.removeEventListener("studysnap-auth-change", syncAuth);
    };
  }, []);

  const derivedSubjects = useMemo(() => {
    const subjectSet = new Set<string>();
    items.forEach((item) => {
      const subject = normalizeSubject(item.subject);
      if (subject) {
        subjectSet.add(subject);
      }
    });
    return Array.from(subjectSet).sort((left, right) => left.localeCompare(right));
  }, [items]);
  const availableSubjects = subjectSuggestions.length > 0 ? subjectSuggestions : derivedSubjects;

  const availableTags = useMemo(() => {
    const tagSet = new Set<string>();
    items.forEach((item) => {
      normalizeTags(item.tags).forEach((tag) => tagSet.add(tag));
    });
    return Array.from(tagSet).sort((left, right) => left.localeCompare(right));
  }, [items]);

  const visibleTagOptions = useMemo(() => {
    const query = tagSearchQuery.trim().toLowerCase();
    if (query.length === 0) {
      return availableTags;
    }
    return availableTags.filter((tag) => tag.toLowerCase().includes(query));
  }, [availableTags, tagSearchQuery]);

  useEffect(() => {
    if (selectedSubject === ALL_SUBJECTS) {
      return;
    }
    if (!availableSubjects.includes(selectedSubject)) {
      setSelectedSubject(ALL_SUBJECTS);
    }
  }, [availableSubjects, selectedSubject]);

  useEffect(() => {
    setSelectedTags((previous) => previous.filter((tag) => availableTags.includes(tag)));
  }, [availableTags]);

  useEffect(() => {
    if (!tagFilterOpen && tagSearchQuery.length > 0) {
      setTagSearchQuery("");
    }
  }, [tagFilterOpen, tagSearchQuery]);

  const hasActiveFilters = searchQuery.trim().length > 0
    || selectedSubject !== ALL_SUBJECTS
    || selectedTags.length > 0;

  const toggleTag = useCallback((tag: string) => {
    setSelectedTags((previous) => (
      previous.includes(tag)
        ? previous.filter((selectedTag) => selectedTag !== tag)
        : [...previous, tag]
    ));
  }, []);

  const clearFilters = useCallback(() => {
    setSearchQuery("");
    setSelectedSubject(ALL_SUBJECTS);
    setSelectedTags([]);
  }, []);

  const filteredItems = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    return items.filter((item) => {
      const title = item.title?.trim() || "Untitled note";
      const tags = normalizeTags(item.tags);
      const titleMatch = query.length === 0
        || title.toLowerCase().includes(query)
        || item.contentPreview.toLowerCase().includes(query)
        || tags.some((tag) => tag.toLowerCase().includes(query));
      const subjectMatch = selectedSubject === ALL_SUBJECTS
        || normalizeSubject(item.subject) === selectedSubject;
      const tagMatch = selectedTags.length === 0
        || selectedTags.some((selectedTag) => tags.includes(selectedTag));
      return titleMatch && subjectMatch && tagMatch;
    });
  }, [items, searchQuery, selectedSubject, selectedTags]);

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      <PageHeader
        eyebrow="LIBRARY"
        title="Public Library"
        description="Explore public notes from you, the community, and official NoteLib examples."
      />

      {loading ? (
        <div className="grid gap-4 md:grid-cols-2">
          {Array.from({ length: 4 }).map((_, index) => (
            <Card key={`public-library-loading-${index}`} className="space-y-3 p-4 sm:p-6">
              <div className="h-5 w-2/3 animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
              <div className="h-4 w-1/2 animate-pulse rounded bg-foreground/10" />
            </Card>
          ))}
        </div>
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h2 className="text-xl font-semibold">Could not load public notes</h2>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" variant="outline" onClick={() => void loadNotes()}>
            Retry
          </Button>
        </Card>
      ) : (
        <div className="space-y-4">
          <Card className="space-y-4 p-4 sm:p-6">
            <div className="grid gap-3 lg:grid-cols-3">
              <div className="space-y-2">
                <label htmlFor="public-library-search" className="text-sm font-medium">
                  Search
                </label>
                <input
                  id="public-library-search"
                  type="search"
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  placeholder="Search public notes..."
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                />
              </div>
              <div className="space-y-2">
                <label htmlFor="public-library-subject" className="text-sm font-medium">
                  Subject
                </label>
                <select
                  id="public-library-subject"
                  value={selectedSubject}
                  onChange={(event) => setSelectedSubject(event.target.value)}
                  className="h-10 w-full rounded-lg border border-border bg-background px-3 text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
                >
                  <option value={ALL_SUBJECTS}>All subjects</option>
                  {availableSubjects.map((subject) => (
                    <option key={subject} value={subject}>
                      {subject}
                    </option>
                  ))}
                </select>
              </div>
              <div className="relative space-y-2">
                <label htmlFor="public-library-tag-filter" className="text-sm font-medium">
                  Tags
                </label>
                <button
                  id="public-library-tag-filter"
                  type="button"
                  className="flex h-10 w-full items-center justify-between rounded-lg border border-border bg-background px-3 text-left text-sm text-foreground outline-none transition-colors focus:ring-2 focus:ring-blue-600"
                  aria-haspopup="listbox"
                  aria-expanded={tagFilterOpen}
                  aria-label="Select tags"
                  onClick={() => setTagFilterOpen((previous) => !previous)}
                >
                  <span className={selectedTags.length === 0 ? "text-foreground/55" : ""}>
                    {selectedTags.length === 0
                      ? "Select tags"
                      : selectedTags.length === 1
                        ? selectedTags[0]
                        : `${selectedTags.length} tags selected`}
                  </span>
                  <ChevronDown className={`h-4 w-4 text-foreground/70 transition-transform ${tagFilterOpen ? "rotate-180" : ""}`} />
                </button>
                {tagFilterOpen ? (
                  <div
                    className="absolute z-30 mt-1 w-full rounded-lg border border-border bg-background p-2 shadow-md"
                    role="listbox"
                    aria-multiselectable="true"
                  >
                    <input
                      type="search"
                      value={tagSearchQuery}
                      onChange={(event) => setTagSearchQuery(event.target.value)}
                      placeholder="Search tags..."
                      className="h-9 w-full rounded-md border border-border bg-background px-2 text-sm text-foreground outline-none transition-colors placeholder:text-foreground/45 focus:ring-2 focus:ring-blue-600"
                    />
                    {availableTags.length === 0 ? (
                      <p className="px-2 py-2 text-sm text-foreground/65">No tags available yet.</p>
                    ) : visibleTagOptions.length === 0 ? (
                      <p className="px-2 py-2 text-sm text-foreground/65">No tags match your search.</p>
                    ) : (
                      <div className="mt-2 max-h-56 space-y-1 overflow-y-auto">
                        {visibleTagOptions.map((tag) => {
                          const isSelected = selectedTags.includes(tag);
                          return (
                            <label
                              key={tag}
                              className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm hover:bg-muted/50"
                            >
                              <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => toggleTag(tag)}
                                className="h-4 w-4 rounded border-border"
                              />
                              <span>{tag}</span>
                            </label>
                          );
                        })}
                      </div>
                    )}
                  </div>
                ) : null}
              </div>
            </div>

            {hasActiveFilters ? (
              <div className="space-y-2 border-t border-border pt-3">
                <div className="flex flex-wrap items-center gap-2">
                  {selectedSubject !== ALL_SUBJECTS ? (
                    <span className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs">
                      Subject: {selectedSubject}
                      <button
                        type="button"
                        className="text-foreground/65 hover:text-foreground"
                        onClick={() => setSelectedSubject(ALL_SUBJECTS)}
                        aria-label="Clear subject filter"
                      >x</button>
                    </span>
                  ) : null}
                  {selectedTags.map((tag) => (
                    <span
                      key={`active-tag-${tag}`}
                      className="inline-flex items-center gap-1 rounded-full border border-border bg-background px-3 py-1 text-xs"
                    >
                      {tag}
                      <button
                        type="button"
                        className="text-foreground/65 hover:text-foreground"
                        onClick={() => setSelectedTags((previous) => previous.filter((value) => value !== tag))}
                        aria-label={`Remove tag filter ${tag}`}
                      >x</button>
                    </span>
                  ))}
                  <Button type="button" variant="outline" size="sm" className="h-8" onClick={clearFilters}>
                    Clear all
                  </Button>
                </div>
              </div>
            ) : null}
          </Card>

          {filteredItems.length === 0 ? (
            <Card className="space-y-3 p-4 sm:p-6">
              <h2 className="text-base font-semibold sm:text-lg">No public notes match your filters.</h2>
              <p className="text-sm text-foreground/75">Try adjusting search or filters.</p>
              <Button type="button" variant="outline" onClick={clearFilters} className="w-full sm:w-auto">
                Clear filters
              </Button>
            </Card>
          ) : (
            <div className="grid gap-4 md:grid-cols-2">
              {filteredItems.map((item) => {
                const itemTags = normalizeTags(item.tags);
                const authorBadge = resolveAuthorBadge(item, currentUserId);

                return (
                  <Card
                    key={item.id}
                    role="link"
                    tabIndex={0}
                    onClick={() => router.push(buildPublicLibraryNotePath({ subject: item.subject, title: item.title }))}
                    onKeyDown={(event) => {
                      if (event.key === "Enter" || event.key === " ") {
                        event.preventDefault();
                        router.push(buildPublicLibraryNotePath({ subject: item.subject, title: item.title }));
                      }
                    }}
                    className="flex h-full cursor-pointer flex-col justify-between space-y-4 p-4 transition-colors hover:bg-muted/40 hover:shadow-md sm:p-6"
                  >
                    <div className="min-w-0 space-y-2">
                      <div className="flex flex-wrap gap-2">
                        <SubjectBadge subject={item.subject} />
                        <span
                          className={`inline-flex items-center rounded-full border px-2 py-1 text-xs font-medium ${authorBadge.className}`}
                        >
                          {authorBadge.label}
                        </span>
                        {authorBadge.showOfficialBadge ? (
                          <span className="inline-flex items-center rounded-full border border-blue-500/35 bg-blue-500/10 px-2 py-1 text-xs font-medium text-blue-700 dark:text-blue-300">
                            Official
                          </span>
                        ) : null}
                      </div>
                      <h3 className="text-base font-semibold sm:text-lg">{item.title?.trim() || "Untitled note"}</h3>
                      <p className="text-sm leading-relaxed text-foreground/75">{toPreview(item.contentPreview)}</p>
                    </div>

                    <div className="flex flex-wrap gap-2">
                      {itemTags.length > 0 ? (
                        itemTags.map((tag) => (
                          <span
                            key={`${item.id}-${tag}`}
                            className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
                          >
                            {tag}
                          </span>
                        ))
                      ) : (
                        <span className="rounded-full border border-dashed border-border px-2 py-1 text-xs text-foreground/55">
                          No tags
                        </span>
                      )}
                    </div>
                  </Card>
                );
              })}
            </div>
          )}
        </div>
      )}
    </main>
  );
}
