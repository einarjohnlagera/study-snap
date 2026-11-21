package com.studysnap.backend.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/** Replays the key-free FRB path without touching the caller's report destination. */
final class ReportRecomputation {
    private ReportRecomputation() {}

    static Map<String, RouteVerdictCalculator.RouteReport> recompute(
            ResultsPipeline.FrbRunInput run, ResultsPipeline.FrbInput frb) {
        Path directory = null;
        try {
            directory = Files.createTempDirectory("frb-report-recomputation-");
            ResultsPipeline.FrbRunInput copy = new ResultsPipeline.FrbRunInput(
                    run.repoRoot(), run.freeze(), run.parameters(), run.mapping(), run.manifests(),
                    run.baselineMini(), run.baseline41(), run.candidate(), run.reserves(),
                    run.compatibilityReports(), run.headCommitTime(), run.ownerDispositions(),
                    directory.resolve("report.json"), run.synthetic());
            return ResultsPipeline.runFrb(copy, frb).routes();
        } catch (IOException | RuntimeException exception) {
            throw new InvalidReportRecomputation("FRB report recomputation failed", exception);
        } finally {
            if (directory != null) {
                try (Stream<Path> paths = Files.walk(directory)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                } catch (IOException exception) {
                    throw new InvalidReportRecomputation("FRB report recomputation cleanup failed", exception);
                }
            }
        }
    }
}
