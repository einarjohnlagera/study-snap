"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  ApiRequestError,
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
import { getAuthUser, getCurrentUserId } from "@/lib/auth";

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
  ocrFlowState: "idle" | "uploading" | "extracting" | "success" | "failure";
  ocrStatusMessage: string | null;
  toastMessage: string | null;
  toastTone: "success" | "error" | "info";
  showToast: (message: string, tone?: "success" | "error" | "info") => void;
  handleGenerateStudyPack: () => Promise<StudyPackResponse | null>;
  handleConfirmText: () => Promise<StudyPackResponse | null>;
  handleClearNotes: () => void;
};

const ALLOWED_IMAGE_TYPES = ["image/png", "image/jpeg", "image/webp"];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

function toFriendlyOcrErrorMessage(message: string) {
  const normalized = message.toLowerCase();
  if (
    normalized.includes("unsupported")
    || normalized.includes("file type")
    || normalized.includes("content type")
    || normalized.includes("format")
  ) {
    return "Unsupported image type. Upload a PNG, JPEG, or WEBP image.";
  }
  if (normalized.includes("too large") || normalized.includes("max") || normalized.includes("size")) {
    return "Image is too large. Try an image smaller than 5 MB.";
  }
  if (
    normalized.includes("no readable text")
    || normalized.includes("no text")
    || normalized.includes("text detected")
    || normalized.includes("text not detected")
  ) {
    return "No readable text was detected. Retake the photo with better lighting and focus, then try again.";
  }
  return "We could not extract text from this image right now. Try another image or paste notes manually.";
}

export function useStudyPack(demoMode: boolean, initialNotesText = ""): UseStudyPackResult {
  const demoTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const ocrStageTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const [notesText, setNotesText] = useState(initialNotesText);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [imageInputKey, setImageInputKey] = useState(0);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [studyPackResult, setStudyPackResult] = useState<StudyPackResponse | null>(null);
  const [generatedAt, setGeneratedAt] = useState<Date | null>(null);
  const [needsConfirmation, setNeedsConfirmation] =
    useState<NeedsTextConfirmationResponse | null>(null);
  const [confirmedText, setConfirmedText] = useState("");
  const [ocrFlowState, setOcrFlowState] =
    useState<"idle" | "uploading" | "extracting" | "success" | "failure">("idle");
  const [ocrStatusMessage, setOcrStatusMessage] = useState<string | null>(null);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [toastTone, setToastTone] = useState<"success" | "error" | "info">("info");

  const canGenerate = useMemo(() => {
    return notesText.trim().length > 0 || imageFile !== null;
  }, [imageFile, notesText]);

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

  const clearOcrStageTimer = () => {
    if (ocrStageTimerRef.current) {
      clearTimeout(ocrStageTimerRef.current);
      ocrStageTimerRef.current = null;
    }
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
      clearOcrStageTimer();
    };
  }, [demoMode]);

  useEffect(() => {
    if (!toastMessage) {
      return;
    }
    const timeout = window.setTimeout(() => {
      setToastMessage(null);
    }, 3200);
    return () => {
      window.clearTimeout(timeout);
    };
  }, [toastMessage]);

  const showToast = (message: string, tone: "success" | "error" | "info" = "info") => {
    setToastTone(tone);
    setToastMessage(message);
  };

  const setImageFileWithValidation = (file: File | null) => {
    setErrorMessage(null);

    if (!file) {
      setImageFile(null);
      setOcrFlowState("idle");
      setOcrStatusMessage(null);
      return;
    }

    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      setImageFile(null);
      setImageInputKey((prev) => prev + 1);
      setOcrFlowState("failure");
      setOcrStatusMessage("Unsupported image type. Upload a PNG, JPEG, or WEBP image.");
      setErrorMessage("Unsupported image type. Upload a PNG, JPEG, or WEBP image.");
      return;
    }

    if (file.size > MAX_IMAGE_BYTES) {
      setImageFile(null);
      setImageInputKey((prev) => prev + 1);
      setOcrFlowState("failure");
      setOcrStatusMessage("Image is too large. Try an image smaller than 5 MB.");
      setErrorMessage("Image is too large. Try an image smaller than 5 MB.");
      return;
    }

    setImageFile(file);
    setOcrFlowState("idle");
    setOcrStatusMessage("Image selected. OCR will start when you generate the Study Pack.");
  };

  const handleGenerateStudyPack = async (): Promise<StudyPackResponse | null> => {
    if (!canGenerate || loading) {
      return null;
    }

    if (demoMode) {
      startDemoGeneration();
      return DEMO_STUDY_PACK_RESULT;
    }
    if (!getCurrentUserId()) {
      setErrorMessage("Sign up and log in to start generating your Study Pack.");
      if (imageFile) {
        setOcrFlowState("failure");
        setOcrStatusMessage("Sign in first, then upload and extract text from your image.");
      }
      return null;
    }
    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setErrorMessage(null);
      showToast("Verify your email before generating a Study Pack.");
      return null;
    }
    if (!authUser.profileType) {
      setErrorMessage("Complete onboarding to start generating your Study Pack.");
      if (imageFile) {
        setOcrFlowState("failure");
        setOcrStatusMessage("Complete onboarding before running OCR from image notes.");
      }
      return null;
    }

    setLoading(true);
    setErrorMessage(null);
    setStudyPackResult(null);
    setNeedsConfirmation(null);

    try {
      if (imageFile) {
        setOcrFlowState("uploading");
        setOcrStatusMessage("Uploading image...");
        clearOcrStageTimer();
        ocrStageTimerRef.current = setTimeout(() => {
          setOcrFlowState("extracting");
          setOcrStatusMessage("Extracting text from your image...");
        }, 700);

        const response = await createStudyPackFromImage(imageFile);
        clearOcrStageTimer();
        if (isNeedsTextConfirmationResponse(response)) {
          setOcrFlowState("success");
          setOcrStatusMessage("Text extracted. Review and edit it before generating your Study Pack.");
          setNeedsConfirmation(response);
          setConfirmedText(response.extractedText);
          return null;
        }
        setOcrFlowState("success");
        setOcrStatusMessage("Text extracted successfully. Your Study Pack is ready.");
        setStudyPackResult(response);
        setGeneratedAt(new Date());
        return response;
      }

      const response = await createStudyPackFromText(notesText);
      setStudyPackResult(response);
      setGeneratedAt(new Date());
      return response;
    } catch (error) {
      clearOcrStageTimer();
      if (error instanceof ApiRequestError && error.code === "EMAIL_VERIFICATION_REQUIRED") {
        setErrorMessage(null);
        showToast("Verify your email before generating a Study Pack.");
        return null;
      }
      const message =
        error instanceof Error
          ? error.message
          : "We could not generate your study pack right now. Please try again.";
      if (imageFile) {
        const friendlyMessage = toFriendlyOcrErrorMessage(message);
        setOcrFlowState("failure");
        setOcrStatusMessage(friendlyMessage);
        setErrorMessage(friendlyMessage);
      } else {
        setErrorMessage(message);
      }
      return null;
    } finally {
      clearOcrStageTimer();
      setLoading(false);
    }
  };

  const handleConfirmText = async (): Promise<StudyPackResponse | null> => {
    if (!needsConfirmation || confirmedText.trim().length === 0 || loading) {
      return null;
    }
    if (demoMode) {
      return null;
    }
    if (!getCurrentUserId()) {
      setErrorMessage("Sign up and log in to start generating your Study Pack.");
      return null;
    }
    const authUser = getAuthUser();
    if (!authUser?.emailVerifiedAt) {
      setErrorMessage(null);
      showToast("Verify your email before generating a Study Pack.");
      return null;
    }
    if (!authUser.profileType) {
      setErrorMessage("Complete onboarding to start generating your Study Pack.");
      return null;
    }

    setLoading(true);
    setErrorMessage(null);
    setStudyPackResult(null);
    setOcrFlowState("extracting");
    setOcrStatusMessage("Generating your Study Pack from edited text...");

    try {
      const response = await confirmStudyPackText(needsConfirmation.id, confirmedText);
      setStudyPackResult(response);
      setGeneratedAt(new Date());
      setNeedsConfirmation(null);
      setOcrFlowState("success");
      setOcrStatusMessage("Done. Your Study Pack was generated from your edited text.");
      return response;
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === "EMAIL_VERIFICATION_REQUIRED") {
        setErrorMessage(null);
        showToast("Verify your email before generating a Study Pack.");
        return null;
      }
      const message =
        error instanceof Error
          ? error.message
          : "We could not generate your study pack right now. Please try again.";
      setOcrFlowState("failure");
      setOcrStatusMessage("Could not continue with edited text. Update the text and try again.");
      setErrorMessage(message);
      return null;
    } finally {
      setLoading(false);
    }
  };

  const handleClearNotes = () => {
    if (demoTimerRef.current) {
      clearTimeout(demoTimerRef.current);
    }
    clearOcrStageTimer();

    setNotesText("");
    setImageFile(null);
    setImageInputKey((prev) => prev + 1);
    setStudyPackResult(null);
    setNeedsConfirmation(null);
    setConfirmedText("");
    setErrorMessage(null);
    setGeneratedAt(null);
    setLoading(false);
    setOcrFlowState("idle");
    setOcrStatusMessage(null);
    setToastMessage(null);
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
    setImageFile: setImageFileWithValidation,
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
    ocrFlowState,
    ocrStatusMessage,
    toastMessage,
    toastTone,
    showToast,
    handleGenerateStudyPack,
    handleConfirmText,
    handleClearNotes,
  };
}

