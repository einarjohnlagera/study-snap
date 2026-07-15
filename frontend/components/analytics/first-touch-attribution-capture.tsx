"use client";

import { useEffect } from "react";
import { captureFirstTouchAttribution } from "@/lib/first-touch-attribution";

export function FirstTouchAttributionCapture() {
  useEffect(() => {
    captureFirstTouchAttribution();
  }, []);

  return null;
}
