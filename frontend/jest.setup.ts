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
