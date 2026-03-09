"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  confirmStudyPackText,
  createStudyPackFromImage,
  createStudyPackFromText,
  isNeedsTextConfirmationResponse,
  type NeedsTextConfirmationResponse,
  type StudyPackResponse,
} from "@/lib/api";
import {
  DEMO_GENERATION_DELAY_MS,
  DEMO_NOTES,
  DEMO_STUDY_PACK_RESULT,
} from "./demo-content";
import { getCurrentUserId } from "@/lib/auth";

type UseStudyPackResult = {
  notesText: string;
  setNotesText: (value: string) => void;
  imageFile: File | null;
  setImageFile: (file: File | null) => void;
  imageInputKey: number;
  loading: boolean;
  errorMessage: string | null;
  studyPackResult: StudyPackResponse | null;
  needsConfirmation: NeedsTextConfirmationResponse | null;
  confirmedText: string;
  setConfirmedText: (value: string) => void;
  canGenerate: boolean;
  generatedLabel: string | null;
  detectedTopic: string | null;
  handleGenerateStudyPack: () => Promise<void>;
  handleConfirmText: () => Promise<void>;
  handleClearNotes: () => void;
};

export function useStudyPack(demoMode: boolean): UseStudyPackResult {
  const demoTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [notesText, setNotesText] = useState("");
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageInputKey, setImageInputKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [studyPackResult, setStudyPackResult] = useState<StudyPackResponse | null>(null);
  const [generatedAt, setGeneratedAt] = useState<Date | null>(null);
  const [needsConfirmation, setNeedsConfirmation] =
    useState<NeedsTextConfirmationResponse | null>(null);
  const [confirmedText, setConfirmedText] = useState("");

  const canGenerate = useMemo(
    () => notesText.trim().length > 0 || imageFile !== null,
    [imageFile, notesText],
  );

  const startDemoGeneration = () => {
    setLoading(true);
    setErrorMessage(null);
    setStudyPackResult(null);
    setNeedsConfirmation(null);

    if (demoTimerRef.current) {
      clearTimeout(demoTimerRef.current);
    }

    demoTimerRef.current = setTimeout(() => {
      setStudyPackResult(DEMO_STUDY_PACK_RESULT);
      setGeneratedAt(new Date());
      setLoading(false);
    }, DEMO_GENERATION_DELAY_MS);
  };

  useEffect(() => {
    if (!demoMode) {
      return;
    }

    setNotesText(DEMO_NOTES);
    setImageFile(null);
    startDemoGeneration();

    return () => {
      if (demoTimerRef.current) {
        clearTimeout(demoTimerRef.current);
      }
    };
  }, [demoMode]);

  const handleGenerateStudyPack = async () => {
    if (!canGenerate || loading) {
      return;
    }

    if (demoMode) {
      startDemoGeneration();
      return;
    }
    if (!getCurrentUserId()) {
      setErrorMessage("Sign up and log in to start generating your Study Pack.");
      return;
    }

    setLoading(true);
    setErrorMessage(null);
    setStudyPackResult(null);
    setNeedsConfirmation(null);

    try {
      if (imageFile) {
        const response = await createStudyPackFromImage(imageFile);
        if (isNeedsTextConfirmationResponse(response)) {
          setNeedsConfirmation(response);
          setConfirmedText(response.extractedText);
          return;
        }
        setStudyPackResult(response);
        setGeneratedAt(new Date());
        return;
      }

      const response = await createStudyPackFromText(notesText);
      setStudyPackResult(response);
      setGeneratedAt(new Date());
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "We could not generate your study pack right now. Please try again.";
      setErrorMessage(message);
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmText = async () => {
    if (!needsConfirmation || confirmedText.trim().length === 0 || loading) {
      return;
    }
    if (demoMode) {
      return;
    }
    if (!getCurrentUserId()) {
      setErrorMessage("Sign up and log in to start generating your Study Pack.");
      return;
    }

    setLoading(true);
    setErrorMessage(null);
    setStudyPackResult(null);

    try {
      const response = await confirmStudyPackText(needsConfirmation.id, confirmedText);
      setStudyPackResult(response);
      setGeneratedAt(new Date());
      setNeedsConfirmation(null);
    } catch (error) {
      const message =
        error instanceof Error
          ? error.message
          : "We could not generate your study pack right now. Please try again.";
      setErrorMessage(message);
    } finally {
      setLoading(false);
    }
  };

  const handleClearNotes = () => {
    if (demoTimerRef.current) {
      clearTimeout(demoTimerRef.current);
    }

    setNotesText("");
    setImageFile(null);
    setImageInputKey((prev) => prev + 1);
    setStudyPackResult(null);
    setNeedsConfirmation(null);
    setConfirmedText("");
    setErrorMessage(null);
    setGeneratedAt(null);
    setLoading(false);
  };

  const generatedLabel = useMemo(() => {
    if (!generatedAt) {
      return null;
    }
    const seconds = Math.floor((Date.now() - generatedAt.getTime()) / 1000);
    if (seconds < 60) {
      return "Generated just now";
    }
    const minutes = Math.floor(seconds / 60);
    return `Generated ${minutes}m ago`;
  }, [generatedAt]);

  const detectedTopic = useMemo(() => {
    if (!studyPackResult) {
      return null;
    }
    const firstConcept = studyPackResult.keyConcepts.find(
      (concept) => concept.trim().length > 0,
    );
    if (firstConcept) {
      return firstConcept;
    }
    return studyPackResult.title;
  }, [studyPackResult]);

  return {
    notesText,
    setNotesText,
    imageFile,
    setImageFile,
    imageInputKey,
    loading,
    errorMessage,
    studyPackResult,
    needsConfirmation,
    confirmedText,
    setConfirmedText,
    canGenerate,
    generatedLabel,
    detectedTopic,
    handleGenerateStudyPack,
    handleConfirmText,
    handleClearNotes,
  };
}

