import { arrayMove } from "@dnd-kit/sortable";

export function moveSelection(ids: string[], noteId: string, direction: "up" | "down") {
  const currentIndex = ids.indexOf(noteId);
  if (currentIndex < 0) {
    return ids;
  }
  const targetIndex = direction === "up" ? currentIndex - 1 : currentIndex + 1;
  if (targetIndex < 0 || targetIndex >= ids.length) {
    return ids;
  }
  const next = [...ids];
  [next[currentIndex], next[targetIndex]] = [next[targetIndex], next[currentIndex]];
  return next;
}

export function reorderSelectedNoteIdsByDrag(ids: string[], activeId: string, overId: string | null | undefined) {
  if (!overId || activeId === overId) {
    return ids;
  }
  const activeIndex = ids.indexOf(activeId);
  const overIndex = ids.indexOf(overId);
  if (activeIndex < 0 || overIndex < 0) {
    return ids;
  }
  return arrayMove(ids, activeIndex, overIndex);
}
