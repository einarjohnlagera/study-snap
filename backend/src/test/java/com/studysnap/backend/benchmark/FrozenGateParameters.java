package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import java.util.Collections;
import java.math.BigInteger;

/** Frozen section 1, 4, 6, 8 and 9 route-gate parameters and exact-byte digest. */
final class FrozenGateParameters {
    private static final String ROUTES = "routes";
    private static final String WORKFLOWS = "workflows";
    private static final String RESERVE_SIZE = "reserveSize";
    private static final String T2_FAIL_COUNT = "t2FailCount";
    private static final String T1_RECHECK_COUNT = "t1RecheckCount";
    private static final String UNUSABLE_FRACTION = "unusableFraction";
    private static final String UNUSABLE_MIN_CASES = "unusableMinCases";
    private static final String SOL_T3_IMPROVEMENT = "solT3Improvement";
    private static final String SOL_T2_IMPROVEMENT = "solT2Improvement";
    private static final String T3_WORST_P = "t3WorstP";
    private static final String T2_WORST_BASELINE = "t2WorstBaseline";
    private static final String T3_WORST_RHO = "t3WorstRho";
    private static final String T2_WORST_RHO = "t2WorstRho";
    private static final String T2_DELTAS = "t2Deltas";
    private static final String PARSE_RATES = "parseRates";
    private static final String MONTE_CARLO_DRAWS = "monteCarloDraws";
    private static final String MONTE_CARLO_SEEDS = "monteCarloSeeds";
    private static final String PER_CASE_EVENT_CAP = "perCaseEventCap";
    private static final String MARGIN = "margin";
    private static final String ROUTE = "route";
    private static final String PRIMARY_SIZE = "primarySize";
    private static final String SUBGROUPS = "subgroups";
    private static final String INVALID_FIELD = "Invalid ";
    private static final String FRB = "frb";
    private static final Set<String> OPTIONAL_ROOT_FIELDS = Set.of(FRB);
    private static final Set<String> FRB_FIELDS = Set.of("routeAlpha", "familyBudget", "classBWorkflows", RESERVE_SIZE);
    private static final Set<String> RATIONAL_FIELDS = Set.of("numerator", "denominator");
    static final class InvalidParameters extends RuntimeException {
        InvalidParameters(String message) { super(message); }
        InvalidParameters(String message, Throwable cause) { super(message, cause); }
    }

    record Workflow(String route, int primarySize, Map<String, Integer> subgroups) {}
    record Route(int margin, List<String> workflows) {}

    private static final Set<String> ROOT_FIELDS = Set.of(ROUTES, WORKFLOWS, RESERVE_SIZE,
            T2_FAIL_COUNT, T1_RECHECK_COUNT, UNUSABLE_FRACTION, UNUSABLE_MIN_CASES,
            SOL_T3_IMPROVEMENT, SOL_T2_IMPROVEMENT, T3_WORST_P, T2_WORST_BASELINE,
            T3_WORST_RHO, T2_WORST_RHO, T2_DELTAS, PARSE_RATES, MONTE_CARLO_DRAWS,
            MONTE_CARLO_SEEDS, PER_CASE_EVENT_CAP);
    final Map<String, Route> routes;
    final Map<String, Workflow> workflows;
    final int reserveSize;
    final int t2FailCount;
    final int t1RecheckCount;
    final double unusableFraction;
    final int unusableMinCases;
    final int solT3Improvement;
    final int solT2Improvement;
    final double t3WorstP;
    final double t2WorstBaseline;
    final double t3WorstRho;
    final double t2WorstRho;
    final List<Double> t2Deltas;
    final List<Double> parseRates;
    final int monteCarloDraws;
    final List<Integer> monteCarloSeeds;
    final int perCaseEventCap;
    final String sha256;
    final boolean frbEnabled;
    final Rational frbRouteAlpha;
    final Rational frbFamilyBudget;
    final Set<String> frbClassBWorkflows;

    private FrozenGateParameters(JsonNode root, String hash) {
        if (!root.isObject()) throw new InvalidParameters("Parameter root must be an object");
        Set<String> keys = Set.copyOf(root.properties().stream().map(Map.Entry::getKey).toList());
        if (!keys.containsAll(ROOT_FIELDS) || !union(ROOT_FIELDS, OPTIONAL_ROOT_FIELDS).containsAll(keys))
            throw new InvalidParameters("Missing or unknown parameter field");
        routes = readRoutes(root.get(ROUTES));
        workflows = readWorkflows(root.get(WORKFLOWS));
        reserveSize = positive(root, RESERVE_SIZE);
        t2FailCount = positive(root, T2_FAIL_COUNT);
        t1RecheckCount = positive(root, T1_RECHECK_COUNT);
        unusableFraction = fraction(root, UNUSABLE_FRACTION);
        unusableMinCases = positive(root, UNUSABLE_MIN_CASES);
        solT3Improvement = positive(root, SOL_T3_IMPROVEMENT);
        solT2Improvement = positive(root, SOL_T2_IMPROVEMENT);
        t3WorstP = fraction(root, T3_WORST_P);
        t2WorstBaseline = fraction(root, T2_WORST_BASELINE);
        t3WorstRho = fraction(root, T3_WORST_RHO);
        t2WorstRho = fraction(root, T2_WORST_RHO);
        t2Deltas = fractions(root, T2_DELTAS);
        parseRates = fractions(root, PARSE_RATES);
        monteCarloDraws = positive(root, MONTE_CARLO_DRAWS);
        monteCarloSeeds = integers(root, MONTE_CARLO_SEEDS);
        perCaseEventCap = positive(root, PER_CASE_EVENT_CAP);
        if (t2FailCount > reserveSize || perCaseEventCap != 1)
            throw new InvalidParameters("Invalid confirmation count or case-event cap");
        if (unusableFraction == 0 || monteCarloSeeds.contains(0)
                || t2Deltas.stream().anyMatch(delta -> t2WorstBaseline + delta > 1))
            throw new InvalidParameters("Out-of-range probability or random seed");
        if (routes.isEmpty() || workflows.isEmpty() || t2Deltas.isEmpty() || parseRates.isEmpty()
                || monteCarloSeeds.isEmpty()) throw new InvalidParameters("Empty required collection");
        for (Map.Entry<String, Workflow> entry : workflows.entrySet()) {
            Workflow workflow = entry.getValue();
            Route route = routes.get(workflow.route());
            if (route == null || !route.workflows().contains(entry.getKey()))
                throw new InvalidParameters("Workflow route mismatch: " + entry.getKey());
            if (!workflow.subgroups().isEmpty()
                    && workflow.subgroups().values().stream().mapToInt(Integer::intValue).sum() != workflow.primarySize())
                throw new InvalidParameters("Subgroup sizes differ from primary size");
        }
        for (Route route : routes.values()) for (String workflow : route.workflows())
            if (!workflows.containsKey(workflow)) throw new InvalidParameters("Unknown route workflow: " + workflow);
        for (Route route : routes.values()) {
            int count = route.workflows().stream().mapToInt(name -> workflows.get(name).primarySize()).sum();
            if (route.margin() > count) throw new InvalidParameters("Route margin exceeds case count");
        }
        frbEnabled = root.has(FRB);
        if (frbEnabled) {
            JsonNode block = root.get(FRB);
            requireFields(block, FRB_FIELDS);
            frbRouteAlpha = rational(block.get("routeAlpha"));
            frbFamilyBudget = rational(block.get("familyBudget"));
            List<String> names = strings(block, "classBWorkflows");
            if (Set.copyOf(names).size() != names.size() || !workflows.keySet().containsAll(names))
                throw new InvalidParameters("Invalid class-B workflows");
            frbClassBWorkflows = Set.copyOf(names);
            int declaredReserve = positive(block, RESERVE_SIZE);
            if (declaredReserve != reserveSize || declaredReserve != 30)
                throw new InvalidParameters("FRB reserve size differs from protocol");
        } else {
            frbRouteAlpha = null;
            frbFamilyBudget = null;
            frbClassBWorkflows = Set.of();
        }
        sha256 = hash;
    }

    int declaredK(String route) {
        Route declaration = routes.get(route);
        if (declaration == null) throw new InvalidParameters("Unknown route: " + route);
        return declaration.workflows().size();
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        java.util.HashSet<String> result = new java.util.HashSet<>(first);
        result.addAll(second);
        return result;
    }

    private static Rational rational(JsonNode node) {
        requireFields(node, RATIONAL_FIELDS);
        JsonNode numeratorNode = node.get("numerator");
        JsonNode denominatorNode = node.get("denominator");
        if (!numeratorNode.isIntegralNumber() || !denominatorNode.isIntegralNumber())
            throw new InvalidParameters("FRB fractions must be integers");
        BigInteger numerator = numeratorNode.bigIntegerValue();
        BigInteger denominator = denominatorNode.bigIntegerValue();
        if (numerator.signum() <= 0 || denominator.signum() <= 0 || numerator.compareTo(denominator) > 0)
            throw new InvalidParameters("FRB fraction outside (0, 1]");
        return new Rational(numerator, denominator);
    }

    static FrozenGateParameters load(Path file) {
        try {
            Path path = file.toAbsolutePath().normalize();
            Path repo = repoRoot(Path.of("").toAbsolutePath().normalize());
            LlmModelBenchmark.Config.rejectInsideRepoForTest(path, repo);
            Path declaration = path.resolveSibling(path.getFileName() + ".sha256");
            LlmModelBenchmark.Config.rejectInsideRepoForTest(declaration, repo);
            byte[] bytes = Files.readAllBytes(path);
            String declared = Files.readString(declaration).trim();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!actual.equals(declared)) throw new InvalidParameters("Declared SHA-256 differs from parameter bytes");
            JsonNode root = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readTree(bytes);
            if (root == null || !root.isObject()) throw new InvalidParameters("Parameter root must be an object");
            return new FrozenGateParameters(root, actual);
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException ex) {
            throw new InvalidParameters("Cannot load frozen gate parameters", ex);
        }
    }

    private static Path repoRoot(Path start) {
        for (Path path = start; path != null; path = path.getParent())
            if (Files.exists(path.resolve(".git"))) return path;
        throw new InvalidParameters("Cannot locate repository path guard root");
    }

    private static Map<String, Route> readRoutes(JsonNode node) {
        if (node == null || !node.isObject()) throw new InvalidParameters("routes must be an object");
        Map<String, Route> result = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            requireFields(entry.getValue(), Set.of(MARGIN, WORKFLOWS));
            int margin = nonnegative(entry.getValue(), MARGIN);
            List<String> names = strings(entry.getValue(), WORKFLOWS);
            if (names.isEmpty() || Set.copyOf(names).size() != names.size())
                throw new InvalidParameters("Invalid route workflows");
            result.put(entry.getKey(), new Route(margin, names));
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Workflow> readWorkflows(JsonNode node) {
        if (node == null || !node.isObject()) throw new InvalidParameters("workflows must be an object");
        Map<String, Workflow> result = new LinkedHashMap<>();
        node.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            requireFields(value, Set.of(ROUTE, PRIMARY_SIZE, SUBGROUPS));
            if (!value.get(ROUTE).isTextual() || value.get(ROUTE).asText().isBlank())
                throw new InvalidParameters("Invalid workflow route");
            JsonNode groups = value.get(SUBGROUPS);
            if (!groups.isObject()) throw new InvalidParameters("subgroups must be an object");
            Map<String, Integer> sizes = new LinkedHashMap<>();
            groups.properties().forEach(group -> {
                if (!group.getValue().isIntegralNumber() || group.getValue().asInt() <= 0)
                    throw new InvalidParameters("Invalid subgroup size");
                sizes.put(group.getKey(), group.getValue().asInt());
            });
            result.put(entry.getKey(), new Workflow(value.get(ROUTE).asText(),
                    positive(value, PRIMARY_SIZE), Collections.unmodifiableMap(sizes)));
        });
        return Collections.unmodifiableMap(result);
    }

    private static void requireFields(JsonNode node, Set<String> fields) {
        if (node == null || !node.isObject() || !Set.copyOf(node.properties().stream()
                .map(Map.Entry::getKey).toList()).equals(fields))
            throw new InvalidParameters("Missing or unknown parameter field");
    }
    private static int positive(JsonNode node, String field) {
        int value = nonnegative(node, field);
        if (value == 0) throw new InvalidParameters("Nonpositive " + field);
        return value;
    }
    private static int nonnegative(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0)
            throw new InvalidParameters(INVALID_FIELD + field);
        return value.asInt();
    }
    private static double fraction(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || value.asDouble() < 0 || value.asDouble() > 1)
            throw new InvalidParameters(INVALID_FIELD + field);
        return value.asDouble();
    }
    private static List<Double> fractions(JsonNode node, String field) {
        JsonNode values = node.get(field);
        if (values == null || !values.isArray()) throw new InvalidParameters(INVALID_FIELD + field);
        return values.valueStream().map(value -> {
            if (!value.isNumber() || value.asDouble() < 0 || value.asDouble() > 1)
                throw new InvalidParameters(INVALID_FIELD + field);
            return value.asDouble();
        }).toList();
    }
    private static List<Integer> integers(JsonNode node, String field) {
        JsonNode values = node.get(field);
        if (values == null || !values.isArray()) throw new InvalidParameters(INVALID_FIELD + field);
        return values.valueStream().map(value -> {
            if (!value.isIntegralNumber() || !value.canConvertToInt())
                throw new InvalidParameters(INVALID_FIELD + field);
            return value.asInt();
        }).toList();
    }
    private static List<String> strings(JsonNode node, String field) {
        JsonNode values = node.get(field);
        if (values == null || !values.isArray()) throw new InvalidParameters(INVALID_FIELD + field);
        return values.valueStream().map(value -> {
            if (!value.isTextual() || value.asText().isBlank()) throw new InvalidParameters(INVALID_FIELD + field);
            return value.asText();
        }).toList();
    }
}
