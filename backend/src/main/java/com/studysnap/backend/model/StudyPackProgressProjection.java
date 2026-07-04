package com.studysnap.backend.model;

/**
 * Marker return type for {@code StudyPackRepository}'s JPQL projection queries. Deliberately not
 * implemented by {@link com.studysnap.backend.entity.StudyPackEntity} (or any other concrete
 * class) — only Spring Data's generated proxy implements it. If the managed domain entity also
 * implemented this exact interface, Spring Data's projection detection breaks and the query throws
 * a runtime ConverterNotFoundException instead of returning a projected view. Do not add
 * {@code implements StudyPackProgressProjection} anywhere.
 */
public interface StudyPackProgressProjection extends StudyPackProgressView {
}
