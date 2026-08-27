"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { getLinkedLearners, type LinkedLearnerResponse } from "@/lib/api";

type NoteRecipientPickerProps = {
  selectedRelationshipIds: string[];
  disabled?: boolean;
  onChange: (relationshipIds: string[]) => void;
};

export function NoteRecipientPicker({
  selectedRelationshipIds,
  disabled = false,
  onChange,
}: Readonly<NoteRecipientPickerProps>) {
  const [connections, setConnections] = useState<LinkedLearnerResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadConnections = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const loaded = await getLinkedLearners();
      setConnections(loaded.filter((connection) => connection.status === "ACCEPTED"));
    } catch {
      setError("Could not load your connections.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadConnections();
  }, [loadConnections]);

  if (loading) {
    return <p className="px-3 py-3 text-sm text-foreground/65">Loading connections…</p>;
  }

  if (error) {
    return (
      <div className="space-y-2 px-3 py-3">
        <p className="text-sm text-red-700 dark:text-red-300">{error}</p>
        <Button type="button" size="sm" variant="outline" onClick={() => void loadConnections()}>
          Retry
        </Button>
      </div>
    );
  }

  if (connections.length === 0) {
    return (
      <div className="space-y-2 px-3 py-3 text-sm">
        <p className="text-foreground/70">Connect with someone before sharing a note privately.</p>
        <Link href="/linked-learners" className="font-medium text-blue-700 hover:underline dark:text-blue-300">
          Invite someone
        </Link>
      </div>
    );
  }

  const acceptedRelationshipIds = new Set(connections.map((connection) => connection.id));
  const acceptedSelection = selectedRelationshipIds.filter((id) => acceptedRelationshipIds.has(id));

  return (
    <div className="max-h-64 space-y-1 overflow-y-auto p-1">
      {connections.map((connection) => {
        const checked = selectedRelationshipIds.includes(connection.id);
        return (
          <label
            key={connection.id}
            className="flex cursor-pointer items-start gap-3 rounded px-3 py-2 hover:bg-highlight"
          >
            <input
              type="checkbox"
              className="mt-1 h-4 w-4"
              checked={checked}
              disabled={disabled}
              onChange={() => onChange(
                checked
                  ? acceptedSelection.filter((id) => id !== connection.id)
                  : [...acceptedSelection, connection.id],
              )}
            />
            <span className="min-w-0">
              <span className="block truncate text-sm font-medium">{connection.counterpartyDisplayName}</span>
              <span className="block truncate text-xs text-foreground/65">{connection.counterpartyEmail}</span>
            </span>
          </label>
        );
      })}
    </div>
  );
}
