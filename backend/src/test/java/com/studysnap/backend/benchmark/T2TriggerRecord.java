package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Preregistration sections 1 and 8: hash-linked primary T2 findings authorizing Stage 2 reserve. */
record T2TriggerRecord(Map<String, String> files, Map<String, String> recheckFiles,
                       Map<String, Set<String>> candidateOnlyCritical, String freezeSha256) {
    static final class InvalidTrigger extends RuntimeException {
        InvalidTrigger(String message) { super(message); }
        InvalidTrigger(String message, Throwable cause) { super(message, cause); }
    }
    static final String CANDIDATE = "candidateReceipt";
    static final String CANDIDATE_OUTPUT = "candidateDeliveredOutputs";
    static final String MINI = "baselineMiniReceipt";
    static final String MINI_OUTPUT = "baselineMiniDeliveredOutputs";
    static final String FOUR_ONE = "baseline41Receipt";
    static final String FOUR_ONE_OUTPUT = "baseline41DeliveredOutputs";
    static final String MAPPING = "mapping";
    static final String PARAMETERS = "parameters";
    static final String A_KEY = "classAKey";
    static final String A_PRIMARY = "classAPrimary";
    static final String A_REVIEWS = "classAReviews";
    static final String A_ADJUDICATIONS = "classAAdjudications";
    static final String A_EXCLUSIONS = "classAExclusions";
    static final String B_KEY = "classBKey";
    static final String B_SCORES = "classBScores";
    static final String SCORER_REGISTRY = "classBScorerRegistry";
    private static final Set<String> KEYS = Set.of(CANDIDATE, CANDIDATE_OUTPUT,
            MINI, MINI_OUTPUT, FOUR_ONE, FOUR_ONE_OUTPUT, MAPPING, PARAMETERS,
            A_KEY, A_PRIMARY, A_REVIEWS, A_ADJUDICATIONS, A_EXCLUSIONS, B_KEY, B_SCORES,
            SCORER_REGISTRY);
    private static final String SHA_SUFFIX = ".sha256";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    Path linkedPath(String key) {
        String link = files.get(key);
        if (link == null || link.lastIndexOf('#') < 1) throw new InvalidTrigger("Missing trigger link: " + key);
        return Path.of(link.substring(0, link.lastIndexOf('#'))).toAbsolutePath().normalize();
    }

    static T2TriggerRecord create(Map<String, Path> paths, Map<String, Path> recheckPaths,
                                  Map<String, Set<String>> findings,
                                  String freezeSha256, Path output) {
        try {
            DeliveredOutputs.guard(output);
            if (!paths.keySet().equals(KEYS)) throw new InvalidTrigger("Incomplete trigger links");
            Map<String, String> entries = new HashMap<>();
            paths.forEach((key, file) -> {
                try { entries.put(key, file.toAbsolutePath().normalize() + "#" + FreezeRecord.sha256(file)); }
                catch (IOException exception) { throw new InvalidTrigger("Missing trigger input " + file, exception); }
            });
            Map<String, String> repeats = new HashMap<>();
            recheckPaths.forEach((key, file) -> {
                try { repeats.put(key, file.toAbsolutePath().normalize() + "#" + FreezeRecord.sha256(file)); }
                catch (IOException exception) { throw new InvalidTrigger("Missing trigger recheck " + file, exception); }
            });
            T2TriggerRecord record = new T2TriggerRecord(Map.copyOf(entries), Map.copyOf(repeats),
                    Map.copyOf(findings), freezeSha256);
            byte[] bytes = JSON.writeValueAsBytes(record);
            Files.write(output, bytes);
            Files.writeString(output.resolveSibling(output.getFileName() + SHA_SUFFIX), FreezeRecord.sha256(bytes));
            return record;
        } catch (IOException exception) { throw new InvalidTrigger("Cannot write trigger " + output, exception); }
    }

    static T2TriggerRecord readAndVerify(Path path, String workflow) {
        try {
            DeliveredOutputs.guard(path);
            byte[] bytes = Files.readAllBytes(path);
            String declared = Files.readString(path.resolveSibling(path.getFileName() + SHA_SUFFIX)).trim();
            if (!FreezeRecord.sha256(bytes).equals(declared)) throw new InvalidTrigger("Trigger file SHA-256 differs: " + path);
            T2TriggerRecord record = JSON.readValue(bytes, T2TriggerRecord.class);
            if (record.files() == null || !record.files().keySet().equals(KEYS)
                    || record.recheckFiles() == null
                    || record.candidateOnlyCritical() == null
                    || record.freezeSha256() == null)
                throw new InvalidTrigger("Incomplete trigger record: " + path);
            Map<String, Path> paths = new HashMap<>();
            for (var item : record.files().entrySet()) {
                int split = item.getValue().lastIndexOf('#');
                if (split < 1) throw new InvalidTrigger("Invalid trigger link: " + item.getKey());
                Path linked = Path.of(item.getValue().substring(0, split));
                if (!MAPPING.equals(item.getKey())) DeliveredOutputs.guard(linked);
                if (!FreezeRecord.sha256(linked).equals(item.getValue().substring(split + 1)))
                    throw new InvalidTrigger("Trigger link SHA-256 differs: " + linked);
                paths.put(item.getKey(), linked);
            }
            Map<String, Path> recheckPaths = new HashMap<>();
            for (var item : record.recheckFiles().entrySet()) {
                int split = item.getValue().lastIndexOf('#');
                if (split < 1) throw new InvalidTrigger("Invalid recheck link: " + item.getKey());
                Path linked = Path.of(item.getValue().substring(0, split));
                DeliveredOutputs.guard(linked);
                if (!FreezeRecord.sha256(linked).equals(item.getValue().substring(split + 1)))
                    throw new InvalidTrigger("Recheck SHA-256 differs: " + linked);
                recheckPaths.put(item.getKey(), linked);
            }
            Map<String, Set<String>> expected = recompute(paths, recheckPaths, record.freezeSha256());
            if (!expected.equals(record.candidateOnlyCritical()))
                throw new InvalidTrigger("Trigger claims " + record.candidateOnlyCritical()
                        + " differ from recomputed " + expected);
            FrozenGateParameters parameters = FrozenGateParameters.load(paths.get(PARAMETERS));
            if (!parameters.workflows.containsKey(workflow)
                    || !expected.containsKey(workflow) || expected.get(workflow).isEmpty())
                throw new InvalidTrigger("No scored primary T2 trigger for " + workflow);
            return record;
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof InvalidTrigger invalid) throw invalid;
            throw new InvalidTrigger("Cannot verify trigger " + path + ": " + exception.getMessage(), exception);
        }
    }

    static Map<String, Set<String>> recompute(Map<String, Path> paths, Map<String, Path> recheckPaths,
            String freezeSha256) {
            EvidenceMapping mapping = EvidenceMapping.load(paths.get(MAPPING));
            FrozenGateParameters parameters = FrozenGateParameters.load(paths.get(PARAMETERS));
            Stage1Receipt candidate = Stage1Receipt.read(paths.get(CANDIDATE));
            Stage1Receipt mini = Stage1Receipt.read(paths.get(MINI));
            Stage1Receipt paid = Stage1Receipt.read(paths.get(FOUR_ONE));
            Map<String, DeliveredOutputs.Line> candidateOutputs = outputRows(paths.get(CANDIDATE_OUTPUT), candidate);
            Map<String, DeliveredOutputs.Line> miniOutputs = outputRows(paths.get(MINI_OUTPUT), mini);
            Map<String, DeliveredOutputs.Line> paidOutputs = outputRows(paths.get(FOUR_ONE_OUTPUT), paid);
            if (!candidate.freezeSha256().equals(freezeSha256)
                    || !mini.freezeSha256().equals(freezeSha256)
                    || !paid.freezeSha256().equals(freezeSha256)
                    || !candidate.caseSetSha256().equals(mini.caseSetSha256())
                    || !candidate.caseSetSha256().equals(paid.caseSetSha256()))
                throw new InvalidTrigger("Trigger receipts differ in freeze or case set");
            for (Stage1Receipt receipt : List.of(candidate, mini, paid)) {
                Path recheckPath = recheckPaths.get(receipt.arm() + "/receipt");
                if (recheckPath == null) continue;
                RecheckReceipt recheck = RecheckReceipt.read(recheckPath);
                if (!receipt.head().equals(recheck.head())
                        || !receipt.freezeSha256().equals(recheck.freezeSha256())
                        || !receipt.caseSetSha256().equals(recheck.caseSetSha256())
                        || !receipt.arm().equals(recheck.arm()) || !receipt.model().equals(recheck.model()))
                    throw new InvalidTrigger("Recheck receipt identity differs: " + recheckPath);
            }
            ScoreEvidence.Loaded scores = ScoreEvidence.load(new ScoreEvidence.FilesA(paths.get(A_KEY),
                    paths.get(A_PRIMARY), paths.get(A_REVIEWS), paths.get(A_ADJUDICATIONS), paths.get(A_EXCLUSIONS)),
                    new ScoreEvidence.FilesB(paths.get(B_KEY), paths.get(B_SCORES),
                            loadScorers(paths.get(SCORER_REGISTRY))), mapping);
            Map<String, Stage1Receipt.CaseRow> baseline = new HashMap<>();
            mini.cases().forEach(row -> baseline.put(row.id(), row));
            paid.cases().forEach(row -> baseline.put(row.id(), row));
            Map<String, Set<String>> expected = new HashMap<>();
            for (Stage1Receipt.CaseRow row : candidate.cases()) {
                if (!parameters.workflows.containsKey(row.workflow()))
                    throw new InvalidTrigger("Unknown trigger workflow " + row.workflow());
                Stage1Receipt.CaseRow other = baseline.get(row.id());
                if (other == null) throw new InvalidTrigger("No baseline for " + row.id());
                if (!row.workflow().equals(other.workflow())
                        || !row.manifestRowSha256().equals(other.manifestRowSha256()))
                    throw new InvalidTrigger("Trigger pair differs: " + row.id());
                ScoreEvidence.Facts c = scores.get(row.id(), candidate.arm());
                String baselineArm = mini.cases().contains(other) ? mini.arm() : paid.arm();
                ScoreEvidence.Facts b = scores.get(row.id(), baselineArm);
                if (scored(candidate, row, c, candidateOutputs, recheckPaths)
                        && scored(mini.cases().contains(other) ? mini : paid,
                            other, b, mini.cases().contains(other) ? miniOutputs : paidOutputs, recheckPaths)
                        && c != null && b != null && c.critical() && !b.critical())
                    expected.computeIfAbsent(row.workflow(), ignored -> new HashSet<>()).add(row.id());
            }
            return Map.copyOf(expected);
    }

    private static Map<String, ClassBScoreFiles.ScorerKind> loadScorers(Path path) {
        try {
            return JSON.readValue(path.toFile(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (IOException exception) {
            throw new InvalidTrigger("Cannot read linked scorer registry " + path, exception);
        }
    }

    private static Map<String, DeliveredOutputs.Line> outputRows(Path path, Stage1Receipt receipt) {
        Map<String, DeliveredOutputs.Line> result = new HashMap<>();
        for (DeliveredOutputs.Line line : DeliveredOutputs.read(path, receipt)) result.put(line.caseId(), line);
        return result;
    }

    private static boolean scored(Stage1Receipt receipt, Stage1Receipt.CaseRow row,
                                  ScoreEvidence.Facts facts, Map<String, DeliveredOutputs.Line> outputs,
                                  Map<String, Path> recheckPaths) {
        if (facts == null) return false;
        if (row.state() == CaseArmOutcome.State.COMPLETE && row.finalSuccess())
            return validScoredOutput(outputs.get(row.id()), facts);
        Path receiptPath = recheckPaths.get(receipt.arm() + "/receipt");
        if (receiptPath == null) return false;
        RecheckReceipt recheck = RecheckReceipt.read(receiptPath);
        if (!recheck.arm().equals(receipt.arm()) || !recheck.freezeSha256().equals(receipt.freezeSha256())
                || !receipt.caseSetSha256().equals(recheck.caseSetSha256()))
            throw new InvalidTrigger("Recheck identity differs: " + receiptPath);
        RecheckReceipt.Case item = recheck.cases().stream()
                .filter(value -> row.id().equals(value.caseId())).findFirst().orElse(null);
        if (item == null || !java.util.Objects.equals(item.originalFailureClass(), row.failureClass())) return false;
        boolean successful = false;
        for (RecheckReceipt.Repeat repeat : item.repeats()) {
            if (repeat.evidence() != RouteVerdictCalculator.Evidence.SCORED
                    || repeat.protocolDeviation() != null) return false;
            Path output = recheckPaths.get(receipt.arm() + "/" + row.id() + "/" + repeat.recheckIndex());
            if (output == null) throw new InvalidTrigger("Missing recheck output for " + row.id());
            DeliveredOutputs.Line delivered = DeliveredOutputs.readRecheck(output, recheck, item, repeat);
            if (repeat.finalFailureClass() == null) {
                if (!validScoredOutput(delivered, facts)) return false;
                successful = true;
            } else if (delivered.state() != CaseArmOutcome.State.FAILED
                    || !repeat.finalFailureClass().equals(delivered.failureClass())) return false;
        }
        return successful;
    }

    private static boolean validScoredOutput(DeliveredOutputs.Line delivered, ScoreEvidence.Facts facts) {
        return delivered != null && delivered.state() == CaseArmOutcome.State.COMPLETE
                && delivered.content() != null
                && (delivered.content().isArray() ? delivered.content().size() == facts.scoredQuestions()
                        : facts.scoredQuestions() == 1);
    }
}
