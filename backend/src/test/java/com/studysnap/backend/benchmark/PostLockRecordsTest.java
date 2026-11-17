package com.studysnap.backend.benchmark;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostLockRecordsTest {
    private static final String PARAMS = "src/test/java/com/studysnap/backend/benchmark/frozen-route-gates-frb.json";
    private static final String ROUTE = "INTERACTIVE_QUIZ";
    private static final String OTHER_ROUTE = "CRITIQUE";
    private static final String WORKFLOW = "NOTE_ADAPTIVE";
    private static final String CREATED = "2026-11-16T00:04:00Z";
    private static final String RESOLVED = "2026-11-16T00:05:00Z";
    @TempDir Path temp;
    private record Fixture(Path root, Path finding, Path lock, PostLockContext context,
                           SafetyFindingStore safety, EvidenceDisputeStore disputes,
                           ArmKeyAccess.OpenedKey opened) {
        FindingsLedger ledger() { return new FindingsLedger(root.resolve("safety"), root.resolve("disputes"), context); }
        String lockHash() throws Exception { return IntegritySupport.detachedHash(lock); }
        SafetyFinding finding(String id, List<String> routes, boolean material) throws Exception {
            return new SafetyFinding(id, CREATED, lockHash(), opened.rows().getFirst().itemId(), WORKFLOW,
                    null, routes, "An incorrect keyed answer", "reviewer", SafetyFinding.Attribution.GENERATOR, material);
        }
    }
    private Fixture fixture() throws Exception {
        Path root = temp.resolve("case");
        Path locked = Files.createDirectories(root.resolve("locked"));
        var export = ArmKeyGateFixtures.classA(locked);
        Path seal = ArmKeyGateFixtures.seal(locked, export.armKey());
        Path finding = ArmKeyGateFixtures.findings(locked);
        Path lock = ArmKeyGateFixtures.lock(locked, seal, finding, LockRecord.Kind.PRIMARY);
        var opened = ArmKeyAccess.open(export.armKey(), seal,
                List.of(ArmKeyGateFixtures.binding(lock, seal, finding)), Set.of(LockRecord.Kind.PRIMARY),
                locked.resolve("access.jsonl"), ArmKeyGateFixtures.OPENED, "post-lock record test");
        Path parameters = root.resolve("parameters.json");
        Files.copy(Path.of(PARAMS), parameters);
        Files.copy(Path.of(PARAMS + ".sha256"), IntegritySupport.detached(parameters));
        var context = new PostLockContext(lock, finding.getParent(), locked.resolve("packets"),
                seal, parameters);
        var safety = SafetyFindingStore.init(root.resolve("safety"), context, "2026-11-16T00:03:00Z");
        var disputes = EvidenceDisputeStore.init(root.resolve("disputes"), context, "2026-11-16T00:03:00Z");
        return new Fixture(root, finding, lock, context, safety, disputes, opened);
    }
    @Test void genesisOpenResolutionAndChecklistAreExact() throws Exception {
        Fixture f = fixture();
        String lockedHash = FreezeRecord.sha256(f.finding());
        assertThat(f.safety().headSha256().seq()).isZero();
        assertThat(f.ledger().checklistLine(ROUTE)).startsWith("no open safety finding\nsafety store head: 0:")
                .contains("\ndispute store head: 0:");
        SafetyFinding finding = f.finding("defect_1", List.of(ROUTE), true);
        f.safety().create(finding, f.opened());
        assertThat(f.ledger().blocksProductionSwitch(ROUTE)).isTrue();
        assertThat(f.ledger().blocksBenchmarkPass(ROUTE)).isTrue();
        assertThat(f.ledger().blocksProductionSwitch(OTHER_ROUTE)).isFalse();
        assertThat(f.ledger().checklistLine(ROUTE)).startsWith("open safety findings or disputes: defect_1\n");
        var before = f.safety().headSha256();
        f.safety().resolve(new SafetyFinding.Resolution("defect_1", f.safety().findingSha256("defect_1"),
                RESOLVED, "Accept with mitigation", "owner", "Product UX reviewed"));
        assertThat(f.ledger().blocksProductionSwitch(ROUTE)).isFalse();
        f.safety().verifyPrefix(before.seq(), before.sha256());
        assertThat(FreezeRecord.sha256(f.finding())).isEqualTo(lockedHash);
    }
    @Test void rejectsTimeLockMutationOverwriteAndInvalidResolution() throws Exception {
        Fixture f = fixture();
        SafetyFinding good = f.finding("defect_1", List.of(ROUTE), true);
        SafetyFinding tooEarly = new SafetyFinding(good.findingId(), ArmKeyGateFixtures.LOCKED,
                good.lockSha256(), good.itemId(), good.workflow(), null, good.affectedRoutes(),
                good.evidence(), good.discoveredBy(), good.attribution(), true);
        assertThatThrownBy(() -> f.safety().create(tooEarly, null)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        SafetyFinding wrongLock = new SafetyFinding(good.findingId(), CREATED, "0".repeat(64), good.itemId(),
                good.workflow(), null, good.affectedRoutes(), good.evidence(), good.discoveredBy(), good.attribution(), true);
        assertThatThrownBy(() -> f.safety().create(wrongLock, null)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        f.safety().create(good, null);
        assertThatThrownBy(() -> f.safety().create(good, null)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        var badHash = new SafetyFinding.Resolution("defect_1", "0".repeat(64), RESOLVED, "written", "owner", "review");
        assertThatThrownBy(() -> f.safety().resolve(badHash)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        var early = new SafetyFinding.Resolution("defect_1", f.safety().findingSha256("defect_1"), CREATED,
                "written", "owner", "review");
        assertThatThrownBy(() -> f.safety().resolve(early)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        var blank = new SafetyFinding.Resolution("defect_1", f.safety().findingSha256("defect_1"), RESOLVED,
                " ", "owner", "review");
        assertThatThrownBy(() -> f.safety().resolve(blank)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        var noUx = new SafetyFinding.Resolution("defect_1", f.safety().findingSha256("defect_1"), RESOLVED,
                "written", "owner", null);
        assertThatThrownBy(() -> f.safety().resolve(noUx)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        f.safety().resolve(new SafetyFinding.Resolution("defect_1", f.safety().findingSha256("defect_1"),
                RESOLVED, "written", "owner", "review"));
        assertThatThrownBy(() -> f.safety().resolve(noUx)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
    }
    @Test void routeAndIdValidationFailClosed() throws Exception {
        Fixture f = fixture();
        for (String id : List.of("UPPER", "bad.resolution", "bad.sha256", "candidate_1")) {
            SafetyFinding bad = f.finding(id, List.of(ROUTE), false);
            assertThatThrownBy(() -> f.safety().create(bad, null)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        }
        for (List<String> routes : List.of(List.<String>of(), List.of(OTHER_ROUTE), List.of("INTERACTIVE_QUIZZ"))) {
            SafetyFinding bad = f.finding("defect_1", routes, false);
            assertThatThrownBy(() -> f.safety().create(bad, null)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        }
        SafetyFinding omittedRoutes = f.finding("defect_1", null, false);
        assertThatThrownBy(() -> f.safety().create(omittedRoutes, null)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        for (String route : List.of("INTERACTIVE_QUIZZ", "ALL_ROUTES")) {
            assertThatThrownBy(() -> f.ledger().openSafetyFindings(route)).isInstanceOf(FindingsLedger.InvalidLedger.class);
            assertThatThrownBy(() -> f.ledger().openDisputes(route)).isInstanceOf(FindingsLedger.InvalidLedger.class);
            assertThatThrownBy(() -> f.ledger().blocksProductionSwitch(route)).isInstanceOf(FindingsLedger.InvalidLedger.class);
            assertThatThrownBy(() -> f.ledger().blocksBenchmarkPass(route)).isInstanceOf(FindingsLedger.InvalidLedger.class);
            assertThatThrownBy(() -> f.ledger().checklistLine(route)).isInstanceOf(FindingsLedger.InvalidLedger.class);
        }
        f.safety().create(f.finding("all_1", List.of("ALL_ROUTES"), false), null);
        assertThat(f.ledger().blocksProductionSwitch(OTHER_ROUTE)).isTrue();
    }
    @Test void missingStoreOrGenesisAndOrphanFilesThrow() throws Exception {
        Fixture f = fixture();
        Path index = f.root().resolve("safety/index.jsonl");
        Files.delete(index);
        assertThatThrownBy(() -> f.ledger().openSafetyFindings(ROUTE)).isInstanceOf(FindingsLedger.InvalidLedger.class);
        Fixture g = fixtureAt("other");
        Files.writeString(g.root().resolve("safety/orphan.json"), "{}");
        assertThatThrownBy(() -> g.ledger().openSafetyFindings(ROUTE)).isInstanceOf(FindingsLedger.InvalidLedger.class);
        Fixture h = fixtureAt("deleted");
        Files.delete(h.root().resolve("safety/index.jsonl"));
        Files.delete(h.root().resolve("safety"));
        assertThatThrownBy(() -> h.ledger().openSafetyFindings(ROUTE)).isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
    @Test void changedLockedFindingRefusesCreationAndAResolutionCannotDemandUxWhenUnflagged() throws Exception {
        Fixture f = fixture();
        SafetyFinding record = f.finding("defect_1", List.of(ROUTE), false);
        Files.writeString(f.finding(), "changed\n");
        assertThatThrownBy(() -> f.safety().create(record, null)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
        Fixture g = fixtureAt("ux");
        g.safety().create(g.finding("defect_1", List.of(ROUTE), false), null);
        var wrongUx = new SafetyFinding.Resolution("defect_1", g.safety().findingSha256("defect_1"),
                RESOLVED, "written", "owner", "unneeded review");
        assertThatThrownBy(() -> g.safety().resolve(wrongUx)).isInstanceOf(SafetyFindingStore.InvalidStore.class);
    }
    @Test void intactIndexedSemanticallyEarlyResolutionLeavesFindingOpen() throws Exception {
        Fixture f = fixture();
        f.safety().create(f.finding("defect_1", List.of(ROUTE), false), null);
        f.safety().resolve(new SafetyFinding.Resolution("defect_1", f.safety().findingSha256("defect_1"),
                RESOLVED, "written", "owner", null));
        Path resolution = f.root().resolve("safety/defect_1.resolution.json");
        ObjectNode record = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(resolution));
        record.put("resolvedAtUtc", CREATED);
        Files.write(resolution, IntegritySupport.JSON.writeValueAsBytes(record));
        Files.writeString(IntegritySupport.detached(resolution), FreezeRecord.sha256(resolution) + "\n");
        Path index = f.root().resolve("safety/index.jsonl");
        String[] lines = Files.readString(index).split("\n");
        ObjectNode entry = (ObjectNode) IntegritySupport.JSON.readTree(lines[2]);
        entry.put("fileSha256", FreezeRecord.sha256(resolution));
        Files.writeString(index, lines[0] + "\n" + lines[1] + "\n" + entry + "\n");
        assertThat(f.ledger().openSafetyFindings(ROUTE)).hasSize(1);
        assertThat(f.ledger().blocksProductionSwitch(ROUTE)).isTrue();
    }
    private Fixture fixtureAt(String ignored) throws Exception {
        // A second @TempDir scope keeps the first corruption intact while creating another independent lock.
        Path old = temp;
        temp = Files.createDirectory(old.resolve("second"));
        return fixture();
    }
    @Test void deletedMiddleFileAndTruncatedTailNeedExternalHead() throws Exception {
        Fixture f = fixture();
        f.safety().create(f.finding("first", List.of(ROUTE), false), null);
        f.safety().create(f.finding("second", List.of(ROUTE), false), null);
        var noted = f.safety().headSha256();
        Path index = f.root().resolve("safety/index.jsonl");
        String body = Files.readString(index);
        Files.writeString(index, body.substring(0, body.lastIndexOf('\n', body.length() - 2) + 1));
        Files.delete(f.root().resolve("safety/second.json"));
        Files.delete(f.root().resolve("safety/second.json.sha256"));
        assertThat(f.ledger().openSafetyFindings(ROUTE)).hasSize(1);
        assertThatThrownBy(() -> f.safety().verifyPrefix(noted.seq(), noted.sha256()))
                .isInstanceOf(SafetyFindingStore.InvalidStore.class);
        Files.delete(f.root().resolve("safety/first.json"));
        Files.delete(f.root().resolve("safety/first.json.sha256"));
        assertThatThrownBy(() -> f.ledger().openSafetyFindings(ROUTE)).isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
    @Test void arm_revealing_ids_are_rejected_by_whole_token_not_by_substring() throws Exception {
        Fixture f = fixture();
        f.safety().create(f.finding("absolute-quality-concern", List.of(ROUTE), false), null);
        f.safety().create(f.finding("harm-in-output", List.of(ROUTE), false), null);
        assertThatThrownBy(() -> f.safety().create(f.finding("candidate-output", List.of(ROUTE), false), null))
                .isInstanceOf(SafetyFindingStore.InvalidStore.class);
        assertThatThrownBy(() -> f.safety().create(f.finding("arm_two", List.of(ROUTE), false), null))
                .isInstanceOf(SafetyFindingStore.InvalidStore.class);
    }
    @Test void notedPrefixWithTheWrongHashIsRefusedEvenWhenTheChainIsIntact() throws Exception {
        Fixture f = fixture();
        f.safety().create(f.finding("first", List.of(ROUTE), false), null);
        var noted = f.safety().headSha256();
        f.safety().verifyPrefix(noted.seq(), noted.sha256());
        String wrong = "0".repeat(64);
        assertThatThrownBy(() -> f.safety().verifyPrefix(noted.seq(), wrong))
                .isInstanceOf(SafetyFindingStore.InvalidStore.class);
        assertThatThrownBy(() -> f.safety().verifyPrefix(noted.seq(), "not-a-digest"))
                .isInstanceOf(SafetyFindingStore.InvalidStore.class);
    }
    @Test void layoutRefusesLockedDirectoriesAndAnIndexThereBreaksLock() throws Exception {
        Fixture f = fixture();
        Path findings = f.finding().getParent();
        Path packets = f.lock().getParent().resolve("packets");
        assertThatThrownBy(() -> SafetyFindingStore.init(findings.resolve("store"), f.context(), CREATED))
                .isInstanceOf(SafetyFindingStore.InvalidStore.class);
        assertThatThrownBy(() -> SafetyFindingStore.init(packets.resolve("store"), f.context(), CREATED))
                .isInstanceOf(SafetyFindingStore.InvalidStore.class);
        Path stray = findings.resolve("index.jsonl");
        Files.writeString(stray, "{}\n");
        LockRecord lock = LockRecord.load(f.lock());
        assertThatThrownBy(() -> LockRecord.verifyFindingsUnchanged(lock, findings))
                .isInstanceOf(LockRecord.LockBroken.class);
    }
    @Test void editedIndexAndDeletedMiddleResolutionFailClosed() throws Exception {
        Fixture f = fixture();
        f.safety().create(f.finding("first", List.of(ROUTE), false), null);
        f.safety().resolve(new SafetyFinding.Resolution("first", f.safety().findingSha256("first"),
                RESOLVED, "written", "owner", null));
        f.safety().create(f.finding("second", List.of(ROUTE), false), null);
        Path resolution = f.root().resolve("safety/first.resolution.json");
        Files.delete(resolution);
        Files.delete(IntegritySupport.detached(resolution));
        assertThatThrownBy(() -> f.ledger().openSafetyFindings(ROUTE)).isInstanceOf(FindingsLedger.InvalidLedger.class);
        Fixture g = fixtureAt("index");
        g.safety().create(g.finding("first", List.of(ROUTE), false), null);
        Path index = g.root().resolve("safety/index.jsonl");
        String body = Files.readString(index);
        Files.writeString(index, body.replace("\"kind\":\"RECORD\"", "\"kind\":\"RESOLUTION\""));
        assertThatThrownBy(() -> g.ledger().openSafetyFindings(ROUTE)).isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
    @Test void editedPredecessorAndReverseInsertionAreDetectedOrSorted() throws Exception {
        Fixture f = fixture();
        f.safety().create(f.finding("z_last", List.of(ROUTE), false), null);
        f.safety().create(f.finding("a_first", List.of(ROUTE), false), null);
        assertThat(f.ledger().openSafetyFindings(ROUTE)).extracting(SafetyFinding::findingId)
                .containsExactly("a_first", "z_last");
        Path index = f.root().resolve("safety/index.jsonl");
        String[] lines = Files.readString(index).split("\n");
        ObjectNode changed = (ObjectNode) IntegritySupport.JSON.readTree(lines[2]);
        changed.put("prevSha256", "0".repeat(64));
        Files.writeString(index, lines[0] + "\n" + lines[1] + "\n" + changed + "\n");
        assertThatThrownBy(() -> f.ledger().openSafetyFindings(ROUTE)).isInstanceOf(FindingsLedger.InvalidLedger.class);
    }
    @Test void stemFieldMismatchAndResolutionTruncationFailSafely() throws Exception {
        Fixture f = fixture();
        f.safety().create(f.finding("first", List.of(ROUTE), false), null);
        Path record = f.root().resolve("safety/first.json");
        ObjectNode node = (ObjectNode) IntegritySupport.JSON.readTree(Files.readAllBytes(record));
        node.put("findingId", "other");
        Files.write(record, IntegritySupport.JSON.writeValueAsBytes(node));
        Files.writeString(IntegritySupport.detached(record), FreezeRecord.sha256(record) + "\n");
        Path index = f.root().resolve("safety/index.jsonl");
        String body = Files.readString(index);
        String[] lines = body.split("\n");
        ObjectNode entry = (ObjectNode) IntegritySupport.JSON.readTree(lines[1]);
        entry.put("fileSha256", FreezeRecord.sha256(record));
        Files.writeString(index, lines[0] + "\n" + entry + "\n");
        assertThatThrownBy(() -> f.ledger().openSafetyFindings(ROUTE)).isInstanceOf(FindingsLedger.InvalidLedger.class);
        Fixture g = fixtureAt("resolution");
        g.safety().create(g.finding("first", List.of(ROUTE), false), null);
        g.safety().resolve(new SafetyFinding.Resolution("first", g.safety().findingSha256("first"),
                RESOLVED, "written", "owner", null));
        var head = g.safety().headSha256();
        Path gIndex = g.root().resolve("safety/index.jsonl");
        String gBody = Files.readString(gIndex);
        Files.writeString(gIndex, gBody.substring(0, gBody.lastIndexOf('\n', gBody.length() - 2) + 1));
        Path gResolution = g.root().resolve("safety/first.resolution.json");
        Files.delete(gResolution); Files.delete(IntegritySupport.detached(gResolution));
        assertThat(g.ledger().openSafetyFindings(ROUTE)).hasSize(1);
        assertThatThrownBy(() -> g.safety().verifyPrefix(head.seq(), head.sha256()))
                .isInstanceOf(SafetyFindingStore.InvalidStore.class);
    }
}
