"use client";

import Link from "next/link";
import type { ComponentProps } from "react";
import { savePublicLibraryReturnUrl } from "@/lib/public-library-url";

type PublicLibraryReturnLinkProps = ComponentProps<typeof Link> & {
  returnUrl: string;
};

/**
 * A Link that saves a Public Library return URL before navigating into a note reached from a
 * filtered context (a related-notes card, a subject-landing or Exam Hub grid) — so the
 * destination note's own PublicLibraryBackLink doesn't discard that context.
 */
export function PublicLibraryReturnLink({ returnUrl, onClick, ...linkProps }: Readonly<PublicLibraryReturnLinkProps>) {
  return (
    <Link
      {...linkProps}
      onClick={(event) => {
        savePublicLibraryReturnUrl(returnUrl);
        onClick?.(event);
      }}
    />
  );
}
