import "@testing-library/jest-dom";
import React from "react";

jest.mock("next/image", () => ({
  __esModule: true,
  default: ({
    src,
    priority: _priority,
    fill: _fill,
    placeholder: _placeholder,
    blurDataURL: _blurDataURL,
    ...props
  }: React.ImgHTMLAttributes<HTMLImageElement> & {
    src: string | { src?: string };
    priority?: boolean;
    fill?: boolean;
    placeholder?: string;
    blurDataURL?: string;
  }) => (
    React.createElement("img", {
      ...props,
      src: typeof src === "string" ? src : src?.src ?? "",
    })
  ),
}));

// ⚠️ SummaryMarkdown is NO LONGER globally mocked. It was, because react-markdown's ESM chain threw
// on import under next/jest -- which meant the component rendering summaries on nine surfaces,
// including SEO-indexed public pages, had no real coverage anywhere. jest.config.ts now transpiles
// that dependency chain instead, so tests render the real component and its math handling.
