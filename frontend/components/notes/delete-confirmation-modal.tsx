"use client";

import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";

type DeleteConfirmationModalProps = {
  isOpen: boolean;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void;
  onCancel: () => void;
};

export function DeleteConfirmationModal({
  isOpen,
  title,
  message,
  confirmText = "Delete note",
  cancelText = "Cancel",
  onConfirm,
  onCancel,
}: DeleteConfirmationModalProps) {
  return (
    <AppModal
      isOpen={isOpen}
      title={title}
      description={message}
      onClose={onCancel}
      actions={(
        <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={onCancel}>
            {cancelText}
          </Button>
          <Button
            type="button"
            variant="outline"
            className="w-full border-red-300 text-red-700 hover:bg-red-50 dark:border-red-800 dark:text-red-300 dark:hover:bg-red-950/40 sm:w-auto"
            onClick={onConfirm}
          >
            {confirmText}
          </Button>
        </div>
      )}
    />
  );
}
