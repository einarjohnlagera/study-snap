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

/** Owner-frozen pilot bars and execution limits. */
final class PilotBars {
    private static final String L_BARS = "bars";
    private static final String L_SCORER_FAMILIES = "scorerFamilies";
    private static final String L_LIMITS = "limits";
    private static final String L_MINCRITICALRECALLCOUNT = "minCriticalRecallCount";
    private static final String L_CRITICALTOTAL = "criticalTotal";
    private static final String L_MINMATERIALAMBIGUITYMATERIALCOUNT = "minMaterialAmbiguityMaterialCount";
    private static final String L_MATERIALAMBIGUITYTOTAL = "materialAmbiguityTotal";
    private static final String L_MINCROSSFAMILYRECALLFRACTION = "minCrossFamilyRecallFraction";
    private static final String L_MINMINORWORDINGCOUNT = "minMinorWordingCount";
    private static final String L_MINORWORDINGTOTAL = "minorWordingTotal";
    private static final String L_MINNONCRITICALFLAGGEDCOUNT = "minNoncriticalFlaggedCount";
    private static final String L_NONCRITICALTOTAL = "noncriticalTotal";
    private static final String L_MAXCLEANCONTROLCRITICALCOUNT = "maxCleanControlCriticalCount";
    private static final String L_CLEANTOTAL = "cleanTotal";
    private static final String L_CLEANQUALITYFAILREFERENCE = "cleanQualityFailReference";
    private static final String L_MINADEQUATERATIONALES = "minAdequateRationales";
    private static final String L_RATEDRATIONALES = "ratedRationales";
    private static final String L_RATIONALESAMPLESEED = "rationaleSampleSeed";
    private static final String L_OWNERFULLREVIEWSIZES = "ownerFullReviewSizes";
    private static final String L_MODEL = "model";
    private static final String L_MAXINVOCATIONS = "maxInvocations";
    private static final String L_MAXRETRIESPERPACKET = "maxRetriesPerPacket";
    private static final String L_ISOLATIONDRYRUNS = "isolationDryRuns";
    private static final String L_ISOLATIONDRYRUNPACKETID = "isolationDryRunPacketId";
    private static final String L_TOKENSTOP = "tokenStop";
    private static final String L_P = "P";
    private static final String L_S = "S";
    private static final String L_INVALID_OWNERFULLREVIEWSIZES = "Invalid ownerFullReviewSizes";
    private static final String L_INVALID_PREFIX = "Invalid ";
    static final class InvalidPilotBars extends RuntimeException {
        InvalidPilotBars(String message) { super(message); }
        InvalidPilotBars(String message, Throwable cause) { super(message, cause); }
    }
    record Limits(String model, int maxInvocations, int maxRetriesPerPacket, int isolationDryRuns,
                  String isolationDryRunPacketId, long tokenStop) {}
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> ROOT_FIELDS = Set.of(L_BARS, L_LIMITS);
    private static final Set<String> BAR_FIELDS = Set.of(L_MINCRITICALRECALLCOUNT, L_CRITICALTOTAL,
            L_MINMATERIALAMBIGUITYMATERIALCOUNT, L_MATERIALAMBIGUITYTOTAL, L_MINCROSSFAMILYRECALLFRACTION,
            L_MINMINORWORDINGCOUNT, L_MINORWORDINGTOTAL, L_MINNONCRITICALFLAGGEDCOUNT, L_NONCRITICALTOTAL,
            L_MAXCLEANCONTROLCRITICALCOUNT, L_CLEANTOTAL, L_CLEANQUALITYFAILREFERENCE, L_MINADEQUATERATIONALES,
            L_RATEDRATIONALES, L_RATIONALESAMPLESEED, L_OWNERFULLREVIEWSIZES);
    private static final Set<String> LIMIT_FIELDS = Set.of(L_MODEL, L_MAXINVOCATIONS, L_MAXRETRIESPERPACKET,
            L_ISOLATIONDRYRUNS, L_ISOLATIONDRYRUNPACKETID, L_TOKENSTOP);
    final int minCriticalRecallCount, criticalTotal, minMaterialAmbiguityMaterialCount, materialAmbiguityTotal;
    final double minCrossFamilyRecallFraction;
    final int minMinorWordingCount, minorWordingTotal, minNoncriticalFlaggedCount, noncriticalTotal;
    final int maxCleanControlCriticalCount, cleanTotal, cleanQualityFailReference;
    final int minAdequateRationales, ratedRationales;
    final long rationaleSampleSeed;
    final List<Integer> ownerFullReviewSizes;
    final Map<String, Limits> limits;
    private final String sha256;
    private final Map<String, ModelFamily> scorerFamilies;

    private PilotBars(JsonNode root, String hash) {
        if (!root.isObject()) throw new InvalidPilotBars("Expected object");
        Set<String> rootFields = new HashSet<>();
        root.fieldNames().forEachRemaining(rootFields::add);
        if (!rootFields.containsAll(ROOT_FIELDS) || !Set.of(L_BARS, L_LIMITS, L_SCORER_FAMILIES).containsAll(rootFields))
            throw new InvalidPilotBars("Missing or unknown bars fields: " + rootFields);
        if (root.has(L_SCORER_FAMILIES)) {
            JsonNode declared = root.path(L_SCORER_FAMILIES);
            fields(declared, Set.of(L_P, L_S));
            Map<String, ModelFamily> parsed = new HashMap<>();
            for (String role : Set.of(L_P, L_S)) {
                try { parsed.put(role, ModelFamily.valueOf(text(declared, role))); }
                catch (IllegalArgumentException exception) { throw new InvalidPilotBars("Invalid scorerFamilies: " + role, exception); }
            }
            if (parsed.get(L_P) != ModelFamily.OPENAI || parsed.get(L_S) != ModelFamily.ANTHROPIC)
                throw new InvalidPilotBars("Scorer family allocation differs");
            scorerFamilies = Map.copyOf(parsed);
        } else scorerFamilies = null;
        JsonNode bars = root.path(L_BARS);
        fields(bars, BAR_FIELDS);
        JsonNode limitsNode = root.path(L_LIMITS);
        fields(limitsNode, Set.of(L_P, L_S));
        criticalTotal = positive(bars, L_CRITICALTOTAL);
        minCriticalRecallCount = bounded(bars, L_MINCRITICALRECALLCOUNT, 0, criticalTotal);
        materialAmbiguityTotal = positive(bars, L_MATERIALAMBIGUITYTOTAL);
        minMaterialAmbiguityMaterialCount = bounded(bars, L_MINMATERIALAMBIGUITYMATERIALCOUNT, 0, materialAmbiguityTotal);
        JsonNode fraction = bars.path(L_MINCROSSFAMILYRECALLFRACTION);
        if (!fraction.isNumber() || !Double.isFinite(fraction.asDouble()) || fraction.asDouble() < 0 || fraction.asDouble() > 1)
            throw new InvalidPilotBars("Invalid minCrossFamilyRecallFraction");
        minCrossFamilyRecallFraction = fraction.asDouble();
        minorWordingTotal = positive(bars, L_MINORWORDINGTOTAL);
        minMinorWordingCount = bounded(bars, L_MINMINORWORDINGCOUNT, 0, minorWordingTotal);
        noncriticalTotal = positive(bars, L_NONCRITICALTOTAL);
        minNoncriticalFlaggedCount = bounded(bars, L_MINNONCRITICALFLAGGEDCOUNT, 0, noncriticalTotal);
        cleanTotal = positive(bars, L_CLEANTOTAL);
        maxCleanControlCriticalCount = bounded(bars, L_MAXCLEANCONTROLCRITICALCOUNT, 0, cleanTotal);
        cleanQualityFailReference = bounded(bars, L_CLEANQUALITYFAILREFERENCE, 0, cleanTotal);
        ratedRationales = positive(bars, L_RATEDRATIONALES);
        minAdequateRationales = bounded(bars, L_MINADEQUATERATIONALES, 0, ratedRationales);
        JsonNode seed = bars.path(L_RATIONALESAMPLESEED);
        if (!seed.isIntegralNumber() || !seed.canConvertToLong()) throw new InvalidPilotBars("Invalid rationaleSampleSeed");
        rationaleSampleSeed = seed.longValue();
        JsonNode sizes = bars.path(L_OWNERFULLREVIEWSIZES);
        if (!sizes.isArray() || sizes.isEmpty()) throw new InvalidPilotBars(L_INVALID_OWNERFULLREVIEWSIZES);
        List<Integer> parsedSizes = new ArrayList<>();
        sizes.forEach(size -> { if (!size.isInt() || size.asInt() <= 0 || parsedSizes.contains(size.asInt()))
            throw new InvalidPilotBars(L_INVALID_OWNERFULLREVIEWSIZES); parsedSizes.add(size.asInt()); });
        ownerFullReviewSizes = List.copyOf(parsedSizes);
        Map<String, Limits> parsedLimits = new HashMap<>();
        for (String role : Set.of(L_P, L_S)) {
            JsonNode node = limitsNode.path(role);
            fields(node, LIMIT_FIELDS);
            parsedLimits.put(role, new Limits(text(node, L_MODEL), positive(node, L_MAXINVOCATIONS),
                    bounded(node, L_MAXRETRIESPERPACKET, 0, Integer.MAX_VALUE),
                    bounded(node, L_ISOLATIONDRYRUNS, 0, Integer.MAX_VALUE),
                    text(node, L_ISOLATIONDRYRUNPACKETID), positiveLong(node, L_TOKENSTOP)));
        }
        limits = Map.copyOf(parsedLimits);
        sha256 = hash;
    }
    static PilotBars load(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            String hash = FreezeRecord.sha256(bytes);
            String declared = Files.readString(file.resolveSibling(file.getFileName() + ".sha256")).trim();
            if (!hash.equals(declared)) throw new InvalidPilotBars("Bars SHA-256 mismatch");
            return new PilotBars(JSON.readTree(bytes), hash);
        } catch (InvalidPilotBars exception) { throw exception; }
        catch (Exception exception) { throw new InvalidPilotBars("Cannot load pilot bars: " + file, exception); }
    }
    String sha256() { return sha256; }
    Map<String, ModelFamily> scorerFamilies() { return scorerFamilies; }
    Limits limits(String role) {
        Limits value = limits.get(role);
        if (value == null) throw new InvalidPilotBars("Unknown role " + role);
        return value;
    }
    private static void fields(JsonNode node, Set<String> expected) {
        if (!node.isObject()) throw new InvalidPilotBars("Expected object");
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new InvalidPilotBars("Missing or unknown bars fields: " + actual);
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new InvalidPilotBars(L_INVALID_PREFIX + field);
        return value.asText();
    }
    private static int positive(JsonNode node, String field) { return bounded(node, field, 1, Integer.MAX_VALUE); }
    private static int bounded(JsonNode node, String field, int min, int max) {
        JsonNode value = node.path(field);
        if (!value.isInt() || value.asInt() < min || value.asInt() > max) throw new InvalidPilotBars(L_INVALID_PREFIX + field);
        return value.asInt();
    }
    private static long positiveLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0)
            throw new InvalidPilotBars(L_INVALID_PREFIX + field);
        return value.longValue();
    }
}
