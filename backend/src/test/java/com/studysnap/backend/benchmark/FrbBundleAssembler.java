package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Assembles file-backed FRB evidence and offers a separate binding check for the run inputs.
 * Neither entry point is wired to the run or switch yet; a hand-built bundle remains possible.
 * Binding proves equality to the files read here, not that those files are the correct frozen files.
 * The class-A reserve manifest and per-export spot plans have no shared run digest role; the latter
 * are represented by their export digests. Access logs, reserve seals and both reserve locks are
 * outside this binding because the class-B assembler does not digest the latter three.
 * The immutable assembly list also retains each class-A P-score path so the findings-directory
 * check compares the original parent path and the original score bytes.
 */
final class FrbBundleAssembler {
    private static final String FREEZE_ROLE = "freeze";
    private static final String PARAMETERS_ROLE = "parameters";
    private static final String MAPPING_ROLE = "mapping";
    private static final String CLASS_A_SEAL_ROLE = "classA.seal";
    private static final String CLASS_A_LOCK_ROLE = "classA.lock";
    private static final String CLASS_A_P_SCORE_ROLE = "classA.scores.p";
    private static final String CLASS_A_EXCLUSIONS_ROLE = "classA.exclusions";
    private static final String PRIMARY_PACKETS_ROLE = "primary.packets";
    private static final String PRIMARY_SCORES_ROLE = "primary.scores";
    private static final String PRIMARY_SEAL_ROLE = "primary.seal";
    private static final String PRIMARY_LOCK_ROLE = "primary.lock";
    private static final String RESERVE_RECORD_ROLE = "reserve.record";
    private static final String WORKFLOW_FIELD = "workflow";
    private static final String SHA_SUFFIX = ".sha256";
    private static final String PACKETS_SUFFIX = ".packets";
    private static final String UNCLASSIFIED_ROLE_MESSAGE = "Unclassified digest role: ";
    private static final String CLASS_A_NAMESPACE_PREFIX = "classA[";
    private static final String NAMESPACE_SEPARATOR = "].";
    private static final String BINDING_MISMATCH_MESSAGE = "Binding differs: ";
    private static final String PRIMARY_ROLE_PREFIX = "primary.";
    private static final String RESERVE_ROLE_PREFIX = "reserve.";
    private static final String A_PRIMARY = "classA.primary";
    private static final String B_PRIMARY = "classB.primary";
    private static final String A_RESERVE = "classA.reserve";
    private static final String B_RESERVE = "classB.reserve";
    private static final String MANIFEST = "manifest.";
    private static final Set<String> COMMON = Set.of(FREEZE_ROLE, PARAMETERS_ROLE, MAPPING_ROLE);
    private static final String A_EQUAL = "manifest.classA.primary";
    private static final String A_PLAN = "manifest.classA.spotCheckPlan";
    private static final Set<String> A_EXPORT = Set.of("classA.packets", CLASS_A_SEAL_ROLE, CLASS_A_LOCK_ROLE,
            CLASS_A_P_SCORE_ROLE, "classA.scores.s", "classA.scores.h", "classA.drawlog",
            "classA.registry", CLASS_A_EXCLUSIONS_ROLE, "classA.expansionDecision");
    private static final Set<String> B_ONLY = Set.of("manifest.classB.primary", "manifest.classB.reserve",
            "candidate.receipt", PRIMARY_PACKETS_ROLE, PRIMARY_SCORES_ROLE, PRIMARY_SEAL_ROLE,
            PRIMARY_LOCK_ROLE, RESERVE_RECORD_ROLE);

    private FrbBundleAssembler() {}

    record Sources(List<ClassASources> classAExports, ClassBSources classB) {
        Sources {
            if (classAExports == null || classAExports.isEmpty() || classAExports.stream().anyMatch(item -> item == null)
                    || classB == null)
                throw new InvalidFrbBundle("Complete class-A exports and class-B sources required");
            classAExports = List.copyOf(classAExports);
        }
    }

    record Assembled(FrbEvidenceBundle bundle, Map<String, String> expansionOutcomeByWorkflow,
            Map<String, ExpansionOutcomes.Finding> expansionFindings, List<ClassAAssembly> classAAssemblies,
            ClassBAssembly classB, SortedMap<String, String> digests) {
        Assembled {
            if (bundle == null || expansionOutcomeByWorkflow == null || expansionFindings == null
                    || classAAssemblies == null || classB == null || digests == null)
                throw new InvalidFrbBundle("Incomplete assembled bundle");
            expansionOutcomeByWorkflow = Collections.unmodifiableMap(new LinkedHashMap<>(expansionOutcomeByWorkflow));
            expansionFindings = Collections.unmodifiableMap(new LinkedHashMap<>(expansionFindings));
            classAAssemblies = classAAssemblies instanceof BoundAssemblies
                    ? classAAssemblies : List.copyOf(classAAssemblies);
            digests = Collections.unmodifiableSortedMap(new TreeMap<>(digests));
        }
    }

    /** Keeps source paths without changing the six-component Assembled record contract. */
    private static final class BoundAssemblies extends java.util.AbstractList<ClassAAssembly> {
        private final List<ClassAAssembly> values;
        private final Map<String, Path> pScoresByExportId;

        private BoundAssemblies(List<ClassAAssembly> values, Map<String, Path> paths) {
            this.values = List.copyOf(values);
            this.pScoresByExportId = Map.copyOf(paths);
        }

        @Override public ClassAAssembly get(int index) { return values.get(index); }
        @Override public int size() { return values.size(); }
        private Path pScores(String exportId) { return pScoresByExportId.get(exportId); }
    }

    static Assembled assemble(Sources sources) {
        if (sources == null) throw new InvalidFrbBundle("Sources required");
        List<ClassAAssembly> parts = new ArrayList<>();
        for (ClassASources source : sources.classAExports()) parts.add(ClassAEvidenceAssembler.assemble(source));
        ClassBAssembly classB = ClassBEvidenceAssembler.assemble(sources.classB());
        return assembleParts(parts, sources.classAExports(), classB, sources.classB());
    }

    /**
     * Test-only seam. Other callers must use assemble: ExpansionOutcomes.derive(ClassAAssembly)
     * trusts the supplied assembly, while assemble supplies only assemblies it just produced.
     */
    static Assembled assembleParts(List<ClassAAssembly> classA, List<ClassASources> classASources,
            ClassBAssembly classB, ClassBSources classBSources) {
        if (classA == null || classASources == null || classA.isEmpty() || classA.size() != classASources.size()
                || classA.stream().anyMatch(item -> item == null) || classASources.stream().anyMatch(item -> item == null)
                || classA.stream().anyMatch(item -> item.facts() == null || item.digests() == null
                        || item.expansionObligations() == null)
                || classASources.stream().anyMatch(item -> item.export() == null
                        || item.export().pScores() == null || item.export().pScores().getParent() == null)
                || classB == null || classBSources == null || classB.digests() == null
                || classB.classBWorkflowNames() == null || classB.primary() == null
                || classB.reserveParts() == null || classB.reserveParts().stream().anyMatch(item -> item == null))
            throw new InvalidFrbBundle("Complete assembly parts and sources required");
        try {
            ClassASources first = classASources.getFirst();
            if (first.freezeFile() == null || first.parametersFile() == null || first.manifests() == null)
                throw new InvalidFrbBundle("Complete frozen class-A sources required");
            FreezeRecord freeze = FreezeRecord.load(first.freezeFile());
            FrozenGateParameters parameters = FrozenGateParameters.load(first.parametersFile());
            Map<String, Path> manifests = new HashMap<>(first.manifests());
            if (classBSources.manifests() == null) throw new InvalidFrbBundle("Class-B manifests required");
            for (String role : List.of(B_PRIMARY, B_RESERVE)) manifests.put(role, classBSources.manifests().get(role));
            for (String role : List.of(A_PRIMARY, A_RESERVE, B_PRIMARY, B_RESERVE))
                if (manifests.get(role) == null) throw new InvalidFrbBundle("Missing manifest: " + role);
            Map<String, Set<String>> aRows = rows(manifests.get(A_PRIMARY), A_PRIMARY, freeze, parameters);
            Map<String, Set<String>> bRows = rows(manifests.get(B_PRIMARY), B_PRIMARY, freeze, parameters);
            Map<String, Set<String>> reserveRows = rows(manifests.get(B_RESERVE), B_RESERVE, freeze, parameters);
            Set<String> expectedAWorkflows = new HashSet<>(parameters.workflows.keySet());
            expectedAWorkflows.removeAll(parameters.frbClassBWorkflows);
            if (!aRows.keySet().equals(expectedAWorkflows) || !bRows.keySet().equals(parameters.frbClassBWorkflows)
                    || !classB.classBWorkflowNames().equals(parameters.frbClassBWorkflows))
                throw new InvalidFrbBundle("Frozen workflow partition differs");
            Set<String> allA = union(aRows), allB = union(bRows);
            Set<String> pipelinePrimary = ResultsPipeline.loadCases(manifests, freeze).keySet();
            if (!pipelinePrimary.equals(unionOf(allA, allB)))
                throw new InvalidFrbBundle("Primary manifest reader differs from pipeline");
            Set<String> pipelineReserve = ResultsPipeline.loadReserveCases(manifests, freeze).keySet();
            if (!pipelineReserve.containsAll(union(reserveRows)))
                throw new InvalidFrbBundle("Reserve manifest reader differs from pipeline");
            if (!classB.primary().requestedQuestionsByCaseId().keySet().equals(allB))
                throw new InvalidFrbBundle("Class-B primary differs from manifest");
            for (FrbEvidenceBundle.ReservePart part : classB.reserveParts()) {
                if (part.workflow() == null || part.facts() == null || part.frozenReserveCaseIds() == null)
                    throw new InvalidFrbBundle("Incomplete class-B reserve part");
                if (!part.frozenReserveCaseIds().equals(reserveRows.getOrDefault(part.workflow(), Set.of())))
                    throw new InvalidFrbBundle("Reserve frozen ids differ from manifest: " + part.workflow());
            }

            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < classA.size(); i++) order.add(i);
            order.sort(java.util.Comparator.comparing(i -> exportId(classA.get(i))));
            Set<String> workflows = new HashSet<>();
            List<ClassAAssembly> ordered = new ArrayList<>();
            Map<String, Path> pScoresByExportId = new HashMap<>();
            List<AdjudicatedLoaded> facts = new ArrayList<>();
            Map<String, String> outcomes = new LinkedHashMap<>();
            Map<String, ExpansionOutcomes.Finding> findings = new LinkedHashMap<>();
            SortedMap<String, String> digests = new TreeMap<>();
            mergeDigests(digests, classB.digests().values(), null, false);
            for (int index : order) {
                ClassAAssembly part = classA.get(index);
                String id = exportId(part);
                if (id.isBlank() || !Collections.disjoint(workflows, part.expansionObligations().keySet()))
                    throw new InvalidFrbBundle("Workflow appears in two class-A exports: " + id);
                workflows.addAll(part.expansionObligations().keySet());
                ordered.add(part);
                pScoresByExportId.put(id, classASources.get(index).export().pScores());
                facts.add(part.facts());
                mergeDigests(digests, part.digests().values(), id, true);
                ExpansionOutcomes.Result result = ExpansionOutcomes.derive(part);
                result.outcomeByWorkflow().keySet().stream().sorted()
                        .forEach(workflow -> putUnique(outcomes, workflow, result.outcomeByWorkflow().get(workflow)));
                result.findings().keySet().stream().sorted()
                        .forEach(workflow -> putUnique(findings, workflow, result.findings().get(workflow)));
            }
            if (!workflows.equals(expectedAWorkflows) || !findings.keySet().equals(expectedAWorkflows))
                throw new InvalidFrbBundle("Class-A workflows lack complete findings");
            FrbEvidenceBundle bundle = FrbEvidenceBundle.assemble(facts, classB.primary(), classB.reserveParts(),
                    allA, allB, classB.decision(), classB.classBWorkflowNames());
            return new Assembled(bundle, outcomes, findings,
                    new BoundAssemblies(ordered, pScoresByExportId), classB, digests);
        } catch (IOException | ResultsPipeline.InvalidInput | FreezeRecord.InvalidFreeze
                | FrozenGateParameters.InvalidParameters exception) {
            throw new InvalidFrbBundle("Cannot read frozen FRB manifests", exception);
        }
    }

    private static String exportId(ClassAAssembly part) {
        return String.join("+", part.expansionObligations().keySet().stream().sorted().toList());
    }

    private static <T> void putUnique(Map<String, T> map, String role, T value) {
        if (map.putIfAbsent(role, value) != null) throw new InvalidFrbBundle("Duplicate workflow: " + role);
    }

    private static Set<String> union(Map<String, Set<String>> rows) {
        Set<String> all = new LinkedHashSet<>();
        rows.values().forEach(all::addAll);
        return all;
    }

    private static Set<String> unionOf(Set<String> first, Set<String> second) {
        Set<String> all = new LinkedHashSet<>(first);
        all.addAll(second);
        return all;
    }

    /** Reader seam for synthetic mapped-workflow tests; production callers use assemble. */
    static Map<String, Set<String>> rows(Path manifestFile, String role, FreezeRecord freeze,
            FrozenGateParameters parameters) throws IOException {
        if (!FreezeRecord.sha256(manifestFile).equals(freeze.manifests().get(manifestFile.toAbsolutePath().toString())))
            throw new InvalidFrbBundle("Unpinned manifest: " + role);
        boolean classA = role.equals(A_PRIMARY);
        Map<String, Set<String>> rows = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (String line : Files.readAllLines(manifestFile, StandardCharsets.UTF_8)) {
            JsonNode row = IntegritySupport.JSON.readTree(line);
            if (row == null || !row.isObject() || !row.path(WORKFLOW_FIELD).isTextual())
                throw new InvalidFrbBundle("Malformed manifest row: " + role);
            String id = row.path(classA ? "caseId" : "fixtureId").asText();
            String workflow = Stage1RunPlan.workflowName(row.path(WORKFLOW_FIELD).asText());
            if (id.isBlank() || !seen.add(id) || !parameters.workflows.containsKey(workflow)
                    || parameters.frbClassBWorkflows.contains(workflow) == classA)
                throw new InvalidFrbBundle("Invalid frozen case or workflow: " + role);
            rows.computeIfAbsent(workflow, ignored -> new LinkedHashSet<>()).add(id);
        }
        return rows;
    }

    private static void mergeDigests(SortedMap<String, String> target, SortedMap<String, String> source,
            String exportId, boolean classA) {
        for (String role : COMMON)
            if (!source.containsKey(role)) throw new InvalidFrbBundle("Missing common digest role: " + role);
        if (classA && (!source.containsKey(A_EQUAL) || !source.containsKey(A_PLAN)))
            throw new InvalidFrbBundle("Missing class-A manifest digest role");
        for (var entry : source.entrySet()) {
            String role = entry.getKey();
            String base = role.endsWith(SHA_SUFFIX) ? role.substring(0, role.length() - 7) : role;
            if (role.endsWith(SHA_SUFFIX) && (base.endsWith(PACKETS_SUFFIX)))
                throw new InvalidFrbBundle(UNCLASSIFIED_ROLE_MESSAGE + role);
            boolean common = COMMON.contains(base);
            boolean equalA = A_EQUAL.equals(base);
            boolean export = A_PLAN.equals(base) || A_EXPORT.contains(base) || base.matches("classA\\.lock\\.finding\\.[0-9]+");
            boolean onlyB = B_ONLY.contains(base) || base.matches("primary\\.finding\\.[0-9]+")
                    || base.matches("reserve\\.[A-Z][A-Z0-9_]*\\.(packets|scores)");
            if (!(common || classA && (equalA || export) || !classA && onlyB))
                throw new InvalidFrbBundle(UNCLASSIFIED_ROLE_MESSAGE + role);
            String merged = classA && export ? CLASS_A_NAMESPACE_PREFIX + exportId + NAMESPACE_SEPARATOR + role : role;
            String prior = target.putIfAbsent(merged, entry.getValue());
            if (prior != null && !prior.equals(entry.getValue()))
                throw new InvalidFrbBundle("Digest differs: " + merged);
        }
    }

    static void requireBound(Assembled assembled, ResultsPipeline.FrbRunInput run,
            ResultsPipeline.FrbInput frb) {
        if (assembled == null || run == null || frb == null || !assembled.bundle().equals(frb.bundle()))
            throw new InvalidFrbBundle("FRB bundle differs from assembled evidence");
        try {
            hash(assembled, FREEZE_ROLE, run.freeze());
            hash(assembled, PARAMETERS_ROLE, run.parameters());
            hash(assembled, MAPPING_ROLE, run.mapping());
            if (run.manifests() == null) throw new InvalidFrbBundle("Run manifests required");
            for (String role : List.of(A_PRIMARY, B_PRIMARY, B_RESERVE))
                hash(assembled, MANIFEST + role, run.manifests().get(role));
            hash(assembled, RESERVE_RECORD_ROLE, frb.reserveRecordFile());
            ResultsPipeline.ReserveVerification verification = frb.verification();
            if (verification == null || !assembled.classB().classBWorkflowNames().equals(verification.workflowNames()))
                throw new InvalidFrbBundle("Verification workflows differ");
            directory(assembled, PRIMARY_PACKETS_ROLE, verification.packetDirectory());
            hash(assembled, PRIMARY_SCORES_ROLE, verification.scoresFile());
            hash(assembled, PRIMARY_SEAL_ROLE, verification.sealFile());
            hash(assembled, PRIMARY_LOCK_ROLE, verification.primaryLockFile());
            bindPrimaries(assembled, frb.provenance());
            bindReserves(assembled, frb.provenance());
        } catch (IOException exception) {
            throw new InvalidFrbBundle("Cannot hash FRB binding input", exception);
        }
    }

    private static void hash(Assembled assembled, String role, Path path) throws IOException {
        String expected = assembled.digests().get(role);
        if (path == null || expected == null || !expected.equals(FreezeRecord.sha256(path)))
            throw new InvalidFrbBundle(BINDING_MISMATCH_MESSAGE + role);
        String sidecar = assembled.digests().get(role + SHA_SUFFIX);
        if (sidecar != null) {
            IntegritySupport.detachedHash(path);
            if (!sidecar.equals(FreezeRecord.sha256(IntegritySupport.detached(path))))
                throw new InvalidFrbBundle(BINDING_MISMATCH_MESSAGE + role + SHA_SUFFIX);
        }
    }

    private static void directory(Assembled assembled, String role, Path path) throws IOException {
        if (path == null) throw new InvalidFrbBundle(BINDING_MISMATCH_MESSAGE + role);
        String expected = assembled.digests().get(role);
        if (expected == null) throw new InvalidFrbBundle(BINDING_MISMATCH_MESSAGE + role);
        List<Path> entries;
        try (var stream = Files.walk(path)) { entries = stream.toList(); }
        if (!expected.equals(SourceDigests.Builder.directoryHash(path, entries)))
            throw new InvalidFrbBundle(BINDING_MISMATCH_MESSAGE + role);
    }

    private static void bindPrimaries(Assembled assembled, ResultsPipeline.ProvenanceLogs provenance)
            throws IOException {
        if (provenance == null || provenance.primaries() == null)
            throw new InvalidFrbBundle("Primary provenance required");
        if (!(assembled.classAAssemblies() instanceof BoundAssemblies bound))
            throw new InvalidFrbBundle("Class-A source paths required for binding");
        Set<String> found = new HashSet<>();
        for (ResultsPipeline.PrimaryExport export : provenance.primaries()) {
            if (export == null || export.sealFile() == null || export.lockFile() == null)
                throw new InvalidFrbBundle("Incomplete primary export");
            String role = null;
            for (ClassAAssembly part : assembled.classAAssemblies()) {
                String prefix = CLASS_A_NAMESPACE_PREFIX + exportId(part) + NAMESPACE_SEPARATOR;
                if (matches(assembled, prefix + CLASS_A_SEAL_ROLE, export.sealFile())
                        && matches(assembled, prefix + CLASS_A_LOCK_ROLE, export.lockFile())) {
                    role = prefix;
                    break;
                }
            }
            if (role == null && matches(assembled, PRIMARY_SEAL_ROLE, export.sealFile())
                    && matches(assembled, PRIMARY_LOCK_ROLE, export.lockFile())) role = PRIMARY_ROLE_PREFIX;
            if (role == null || !found.add(role)) throw new InvalidFrbBundle("Unassembled primary export");
            if (role.equals(PRIMARY_ROLE_PREFIX)) {
                if (export.exclusionsFileOrNull() != null) throw new InvalidFrbBundle("Class-B exclusions differ");
            } else {
                hash(assembled, role + CLASS_A_SEAL_ROLE, export.sealFile());
                hash(assembled, role + CLASS_A_LOCK_ROLE, export.lockFile());
                String id = role.substring(CLASS_A_NAMESPACE_PREFIX.length(), role.length() - NAMESPACE_SEPARATOR.length());
                Path pScores = bound.pScores(id);
                if (pScores == null || !pScores.getParent().equals(export.findingsDirectory()))
                    throw new InvalidFrbBundle("Class-A findings directory differs");
                hash(assembled, role + CLASS_A_P_SCORE_ROLE, pScores);
                if (!assembled.digests().containsKey(role + CLASS_A_EXCLUSIONS_ROLE)) {
                    if (export.exclusionsFileOrNull() != null)
                        throw new InvalidFrbBundle("Class-A exclusions differ");
                } else hash(assembled, role + CLASS_A_EXCLUSIONS_ROLE, export.exclusionsFileOrNull());
            }
        }
        if (found.size() != assembled.classAAssemblies().size() + 1)
            throw new InvalidFrbBundle("Missing assembled primary export");
    }

    private static boolean matches(Assembled assembled, String role, Path path) throws IOException {
        return assembled.digests().get(role) != null
                && assembled.digests().get(role).equals(FreezeRecord.sha256(path));
    }

    private static void bindReserves(Assembled assembled, ResultsPipeline.ProvenanceLogs provenance)
            throws IOException {
        if (provenance.reserves() == null) throw new InvalidFrbBundle("Reserve provenance required");
        Set<String> drawn = new HashSet<>();
        assembled.classB().decision().reserveDrawnByWorkflow().forEach((workflow, value) -> {
            if (Boolean.TRUE.equals(value)) drawn.add(workflow);
        });
        Set<String> supplied = new HashSet<>();
        assembled.classB().reserveParts().forEach(part -> supplied.add(part.workflow()));
        if (!supplied.equals(drawn))
            throw new InvalidFrbBundle("Drawn reserves lack assembled parts");
        if (!provenance.reserves().keySet().equals(drawn))
            throw new InvalidFrbBundle("Reserve exports differ from assembled parts");
        for (String workflow : drawn) {
            ResultsPipeline.ReserveExport export = provenance.reserves().get(workflow);
            if (export == null || !workflow.equals(export.workflow()) || export.reservePacketDirectory() == null
                    || export.scoresFile() == null)
                throw new InvalidFrbBundle("Drawn reserve lacks packets or scores: " + workflow);
            directory(assembled, RESERVE_ROLE_PREFIX + workflow + PACKETS_SUFFIX, export.reservePacketDirectory());
            hash(assembled, RESERVE_ROLE_PREFIX + workflow + ".scores", export.scoresFile());
        }
    }
}

final class InvalidFrbBundle extends RuntimeException {
    InvalidFrbBundle(String message) { super(message); }
    InvalidFrbBundle(String message, Throwable cause) { super(message, cause); }
}
