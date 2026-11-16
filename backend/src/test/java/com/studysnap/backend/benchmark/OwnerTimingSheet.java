package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OwnerTimingSheet {
    private static final String L_USAGEMETER = "usageMeter";
    private static final String L_FULLREVIEWS = "fullReviews";
    private static final String L_FLAGGEDONLYREVIEWS = "flaggedOnlyReviews";
    private static final String L_RATIONALERATINGS = "rationaleRatings";
    private static final String L_BEFORE = "before";
    private static final String L_AFTER = "after";
    private static final String L_UNIT = "unit";
    private static final String L_PACKETID = "packetId";
    private static final String L_QUESTIONS = "questions";
    private static final String L_MINUTES = "minutes";
    private static final String L_FLAGGEDQUESTIONS = "flaggedQuestions";
    private static final String L_ITEMID = "itemId";
    private static final String L_SCORERROLE = "scorerRole";
    private static final String L_ADEQUATE = "adequate";
    private static final String L_P = "P";
    private static final String L_S = "S";
    private static final String L_INVALID_PREFIX = "Invalid ";
    static final class InvalidTimingSheet extends RuntimeException {
        InvalidTimingSheet(String message) { super(message); }
        InvalidTimingSheet(String message, Throwable cause) { super(message, cause); }
    }
    record UsageMeter(Double before, Double after, String unit) {}
    record FullReview(String packetId, int questions, double minutes) {}
    record FlaggedOnlyReview(String packetId, int flaggedQuestions, double minutes) {}
    record RationaleRating(String itemId, String scorerRole, boolean adequate) {}
    record FullRate(String packetId, int questions, double minutes, double minutesPerQuestion) {}
    record Summary(List<FullRate> fullRates, Map<Integer, Double> minutesPerQuestionBySize,
                   double totalFullMinutes, int totalFullQuestions, double flaggedMinutes,
                   int flaggedQuestions, double flaggedMinutesPerQuestion, Map<String, Integer> ratingCounts) {}
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final UsageMeter usageMeter;
    private final List<FullReview> fullReviews;
    private final List<FlaggedOnlyReview> flaggedOnlyReviews;
    private final List<RationaleRating> rationaleRatings;
    private OwnerTimingSheet(UsageMeter meter, List<FullReview> full, List<FlaggedOnlyReview> flagged,
                             List<RationaleRating> ratings) {
        usageMeter = meter; fullReviews = List.copyOf(full); flaggedOnlyReviews = List.copyOf(flagged);
        rationaleRatings = List.copyOf(ratings);
    }
    UsageMeter usageMeter() { return usageMeter; }
    List<RationaleRating> rationaleRatings() { return rationaleRatings; }
    static OwnerTimingSheet load(Path file) {
        try {
            JsonNode root = JSON.readTree(Files.readAllBytes(file));
            fields(root, Set.of(L_USAGEMETER, L_FULLREVIEWS, L_FLAGGEDONLYREVIEWS, L_RATIONALERATINGS));
            JsonNode meter = root.path(L_USAGEMETER);
            fields(meter, Set.of(L_BEFORE, L_AFTER, L_UNIT));
            UsageMeter usage = new UsageMeter(optionalNumber(meter.path(L_BEFORE)), optionalNumber(meter.path(L_AFTER)),
                    text(meter, L_UNIT));
            List<FullReview> full = new ArrayList<>();
            for (JsonNode row : array(root, L_FULLREVIEWS)) {
                fields(row, Set.of(L_PACKETID, L_QUESTIONS, L_MINUTES));
                full.add(new FullReview(text(row, L_PACKETID), positiveInt(row, L_QUESTIONS), positiveDouble(row, L_MINUTES)));
            }
            List<FlaggedOnlyReview> flagged = new ArrayList<>();
            for (JsonNode row : array(root, L_FLAGGEDONLYREVIEWS)) {
                fields(row, Set.of(L_PACKETID, L_FLAGGEDQUESTIONS, L_MINUTES));
                flagged.add(new FlaggedOnlyReview(text(row, L_PACKETID), positiveInt(row, L_FLAGGEDQUESTIONS),
                        positiveDouble(row, L_MINUTES)));
            }
            List<RationaleRating> ratings = new ArrayList<>();
            for (JsonNode row : array(root, L_RATIONALERATINGS)) {
                fields(row, Set.of(L_ITEMID, L_SCORERROLE, L_ADEQUATE));
                if (!row.path(L_ADEQUATE).isBoolean()) throw new InvalidTimingSheet("Invalid adequate");
                ratings.add(new RationaleRating(text(row, L_ITEMID), text(row, L_SCORERROLE), row.path(L_ADEQUATE).asBoolean()));
            }
            return new OwnerTimingSheet(usage, full, flagged, ratings);
        } catch (InvalidTimingSheet exception) { throw exception; }
        catch (Exception exception) { throw new InvalidTimingSheet("Cannot load owner timing sheet", exception); }
    }
    static Summary summarize(OwnerTimingSheet sheet, PilotKey key, PilotBars bars) {
        if (sheet == null || key == null || bars == null) throw new InvalidTimingSheet("Sheet, key and bars required");
        Map<String, Integer> sizes = key.packetSizes();
        Set<Integer> expectedSizes = Set.copyOf(bars.ownerFullReviewSizes);
        Set<Integer> seenSizes = new HashSet<>();
        Set<String> seenPackets = new HashSet<>();
        List<FullRate> rates = new ArrayList<>();
        Map<Integer, Double> bySize = new HashMap<>();
        double totalMinutes = 0;
        int totalQuestions = 0;
        for (FullReview review : sheet.fullReviews) {
            Integer size = sizes.get(review.packetId());
            if (size == null || !expectedSizes.contains(size) || review.questions() != size
                    || !seenSizes.add(size) || !seenPackets.add(review.packetId()) || !validMinutes(review.minutes()))
                throw new InvalidTimingSheet("Invalid full review " + review.packetId());
            double rate = review.minutes() / size;
            rates.add(new FullRate(review.packetId(), size, review.minutes(), rate));
            bySize.put(size, rate);
            totalMinutes += review.minutes();
            totalQuestions += size;
        }
        if (!seenSizes.equals(expectedSizes)) throw new InvalidTimingSheet("Missing required full review sizes");
        int flaggedQuestions = 0;
        double flaggedMinutes = 0;
        seenPackets.clear();
        for (FlaggedOnlyReview review : sheet.flaggedOnlyReviews) {
            Integer size = sizes.get(review.packetId());
            if (size == null || review.flaggedQuestions() < 1 || review.flaggedQuestions() > size
                    || !validMinutes(review.minutes()) || !seenPackets.add(review.packetId()))
                throw new InvalidTimingSheet("Invalid flagged-only review " + review.packetId());
            flaggedQuestions += review.flaggedQuestions();
            flaggedMinutes += review.minutes();
        }
        Set<String> ids = new HashSet<>();
        key.rows().forEach(row -> ids.add(row.id()));
        Set<String> ratingKeys = new HashSet<>();
        Map<String, Integer> counts = new HashMap<>(Map.of(L_P, 0, L_S, 0));
        for (RationaleRating rating : sheet.rationaleRatings) {
            if (!ids.contains(rating.itemId()) || !Set.of(L_P, L_S).contains(rating.scorerRole())
                    || !ratingKeys.add(rating.scorerRole() + ":" + rating.itemId()))
                throw new InvalidTimingSheet("Invalid rationale rating " + rating.itemId());
            if (rating.adequate()) counts.merge(rating.scorerRole(), 1, Integer::sum);
        }
        return new Summary(List.copyOf(rates), Map.copyOf(bySize), totalMinutes, totalQuestions,
                flaggedMinutes, flaggedQuestions, flaggedQuestions == 0 ? Double.NaN : flaggedMinutes / flaggedQuestions,
                Map.copyOf(counts));
    }
    private static void fields(JsonNode node, Set<String> names) {
        if (!node.isObject()) throw new InvalidTimingSheet("Expected object");
        Set<String> actual = new HashSet<>(); node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(names)) throw new InvalidTimingSheet("Missing or unknown fields: " + actual);
    }
    private static JsonNode array(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isArray()) throw new InvalidTimingSheet(L_INVALID_PREFIX + name);
        return value;
    }
    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isTextual() || value.asText().isBlank()) throw new InvalidTimingSheet(L_INVALID_PREFIX + name);
        return value.asText();
    }
    private static Double optionalNumber(JsonNode node) {
        if (node.isNull()) return null;
        if (!node.isNumber() || !Double.isFinite(node.asDouble())) throw new InvalidTimingSheet("Invalid usage meter number");
        return node.asDouble();
    }
    private static int positiveInt(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isInt() || value.asInt() <= 0) throw new InvalidTimingSheet(L_INVALID_PREFIX + name);
        return value.asInt();
    }
    private static double positiveDouble(JsonNode node, String name) {
        JsonNode value = node.path(name);
        if (!value.isNumber() || !validMinutes(value.asDouble())) throw new InvalidTimingSheet(L_INVALID_PREFIX + name);
        return value.asDouble();
    }
    private static boolean validMinutes(double minutes) { return Double.isFinite(minutes) && minutes > 0; }
}
