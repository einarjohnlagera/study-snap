"use client";

import type { AuthUser } from "./auth";
import type { MeResponse } from "./api";

export type FirstStudyOnboardingStep = "create-note" | "saved-note" | "study-pack-ready";

const STORAGE_PREFIX = "notelib-first-study-onboarding";

function buildStorageKey(userId: string): string {
  return `${STORAGE_PREFIX}:${userId}`;
}

type StoredState = {
  step: FirstStudyOnboardingStep;
};

export function isFirstStudyOnboardingEligible(me: Pick<MeResponse, "emailVerifiedAt" | "productOnboardingCompletedAt" | "studyPackCount">): boolean {
  return Boolean(me.emailVerifiedAt) && me.productOnboardingCompletedAt === null && me.studyPackCount === 0;
}

export function hasPendingFirstStudyOnboarding(authUser: Pick<AuthUser, "id" | "productOnboardingCompletedAt"> | null): boolean {
  if (!authUser?.id || authUser.productOnboardingCompletedAt) {
    return false;
  }
  return getFirstStudyOnboardingStep(authUser.id) !== null;
}

export function getFirstStudyOnboardingStep(userId: string): FirstStudyOnboardingStep | null {
  if (globalThis.window === undefined) {
    return null;
  }
  const raw = globalThis.localStorage.getItem(buildStorageKey(userId));
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as StoredState;
    return parsed.step ?? null;
  } catch {
    return null;
  }
}

export function setFirstStudyOnboardingStep(userId: string, step: FirstStudyOnboardingStep): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.setItem(buildStorageKey(userId), JSON.stringify({ step }));
}

export function clearFirstStudyOnboardingStep(userId: string): void {
  if (globalThis.window === undefined) {
    return;
  }
  globalThis.localStorage.removeItem(buildStorageKey(userId));
}
