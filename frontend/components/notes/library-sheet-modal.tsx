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
      panelClassName="max-h-[85vh] w-full max-w-full self-end overflow-hidden rounded-b-none rounded-t-2xl p-4 sm:max-h-[80vh] sm:max-w-xl sm:self-auto sm:rounded-xl sm:p-5"
      actions={actions}
    >
      <div className="max-h-[58vh] space-y-5 overflow-y-auto pr-1 sm:max-h-[55vh]">
        {children}
      </div>
    </AppModal>
  );
}
