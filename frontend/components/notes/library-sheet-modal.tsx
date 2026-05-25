"use client";

import type { ReactNode } from "react";
import { AppModal } from "@/components/ui/app-modal";

type LibrarySheetModalProps = {
  isOpen: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
  actions?: ReactNode;
};

export function LibrarySheetModal({
  isOpen,
  title,
  onClose,
  children,
  actions,
}: Readonly<LibrarySheetModalProps>) {
  return (
    <AppModal
      isOpen={isOpen}
      title={title}
      onClose={onClose}
      variant="sheet"
      panelClassName="sm:max-w-xl"
      actions={actions}
    >
      <div className="max-h-[58dvh] space-y-5 overflow-y-auto pr-1 sm:max-h-[55dvh]">
        {children}
      </div>
    </AppModal>
  );
}
