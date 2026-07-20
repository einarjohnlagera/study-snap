package com.studysnap.backend.model;

/**
 * Marker return type for {@code NoteRepository}'s JPQL list-item projection queries. Deliberately
 * not implemented by {@link com.studysnap.backend.entity.NoteEntity} (or any other concrete
 * class) — only Spring Data's generated proxy implements it. If the managed domain entity also
 * implemented this exact interface, Spring Data's projection detection breaks and the query throws
 * a runtime ConverterNotFoundException instead of returning a projected view. Do not add
 * {@code implements NoteListItemProjection} anywhere.
 */
public interface NoteListItemProjection extends NoteListItemView {
}
