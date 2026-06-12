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

// react-markdown (and its remark/micromark deps) ship pure ESM that next/jest does
// not transpile, so any suite rendering SummaryMarkdown crashes on import. Mock the
// wrapper to render its content as plain text — text-based assertions still pass.
jest.mock("@/components/ui/summary-markdown", () => ({
  __esModule: true,
  SummaryMarkdown: ({ content, className }: { content: string; className?: string }) =>
    React.createElement("div", { className, "data-testid": "summary-markdown" }, content),
}));
