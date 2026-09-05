package com.studysnap.backend.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class NoteCollectionItemRemovalId implements Serializable {
    private UUID adoptedCollectionId;
    private UUID sourcePlanId;
    private UUID sourceNoteId;
}
