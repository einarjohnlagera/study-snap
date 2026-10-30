package com.studysnap.backend.repository;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One immutable generation of the tag membership and engagement inputs to ranked listing SQL. */
public record PublicLibraryRankingSnapshot(
        long generation,
        OffsetDateTime builtAt,
        Map<UUID, Long> copies,
        Map<UUID, Long> likes,
        Map<UUID, Long> views,
        Map<String, Set<UUID>> tagIds,
        long publicNotes,
        long tagEntries
) {
    public PublicLibraryRankingSnapshot {
        copies = Map.copyOf(copies);
        likes = Map.copyOf(likes);
        views = Map.copyOf(views);
        tagIds = tagIds.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }

    public Set<UUID> idsFor(java.util.List<String> slugs) {
        Set<UUID> ids = new HashSet<>();
        for (String slug : slugs) {
            ids.addAll(tagIds.getOrDefault(slug, Set.of()));
        }
        return ids;
    }
}
