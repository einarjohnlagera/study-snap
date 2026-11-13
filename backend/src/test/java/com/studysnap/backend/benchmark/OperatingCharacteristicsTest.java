package com.studysnap.backend.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Executed section 1 table and hash oracles. */
class OperatingCharacteristicsTest {
    private static final String UNKNOWN_RULE = "unknownRule";
    private static final String INTERACTIVE = GateTestFixture.INTERACTIVE;
    private static final String PAID = GateTestFixture.PAID;
    private static final String CRITIQUE = GateTestFixture.CRITIQUE;
    private static final List<String> ROUTES = List.of(INTERACTIVE, PAID, CRITIQUE);
    @TempDir Path temp;

    /** The Revision 2.1 parameter set (study tip N=12, Interactive margin D<=10) that the published section-1
     * tables were computed for, derived from the current frozen file so those historical oracles stay executed. */
    FrozenGateParameters parameters() throws IOException, NoSuchAlgorithmException {
        ObjectNode tree = (ObjectNode) new ObjectMapper().readTree(Files.readString(GateTestFixture.SOURCE));
        ((ObjectNode) tree.path("workflows").path("STUDY_TIP")).put("primarySize", 12);
        ((ObjectNode) tree.path("routes").path(INTERACTIVE)).put("margin", 10);
        Path file = temp.resolve("revision21-" + GateTestFixture.FILE_NAME);
        writeAndHash(file, tree.toString());
        return FrozenGateParameters.load(file);
    }

    /** The current frozen file (Revision 2.2: study tip N=30, Interactive margin D<=11), byte for byte. */
    FrozenGateParameters currentParameters() throws IOException {
        Path file = temp.resolve(GateTestFixture.FILE_NAME);
        Files.copy(GateTestFixture.SOURCE, file);
        Files.copy(Path.of(GateTestFixture.SOURCE + GateTestFixture.SHA_SUFFIX),
                temp.resolve(GateTestFixture.FILE_NAME + GateTestFixture.SHA_SUFFIX));
        return FrozenGateParameters.load(file);
    }

    /** Revision 2.2 oracle for the 180-case Interactive route at D<=11, computed by the release session with a
     * second exact method (binomial difference for T3) and confirmed by MT19937 Monte Carlo (<=.002). */
    @Test void revision22InteractiveOracleAt180CasesAndMarginEleven() throws IOException {
        FrozenGateParameters p = currentParameters();
        assertThat(p.workflows.get("STUDY_TIP").primarySize()).isEqualTo(30);
        assertThat(p.routes.get(INTERACTIVE).margin()).isEqualTo(11);
        int cases = p.routes.get(INTERACTIVE).workflows().stream()
                .mapToInt(name -> p.workflows.get(name).primarySize()).sum();
        assertThat(cases).isEqualTo(180);
        double t3 = OperatingCharacteristics.t3Fail(cases, 11, .5, .5, 0);
        double t2 = OperatingCharacteristics.t2Fail(p, INTERACTIVE, .02, .02, 0);
        assertThat(t3).isEqualTo(.112692, org.assertj.core.data.Offset.offset(.0000015));
        assertThat(t2).isEqualTo(.054099, org.assertj.core.data.Offset.offset(.0000015));
        assertThat(1 - (1 - t3) * (1 - t2)).isEqualTo(.160694, org.assertj.core.data.Offset.offset(.0000015));
        assertThat(t3 + t2).isEqualTo(.166791, org.assertj.core.data.Offset.offset(.0000015));
        double[] rates = {0, .005, .01, .02, .05, .06};
        double[] equivalent = {.839306, .839279, .839090, .837599, .813826, .796217};
        double[] plusFive = {.105097, .105093, .105063, .104826, .101064, .098296};
        double[] plusTen = {.000862, .000862, .000862, .000860, .000827, .000804};
        for (int i = 0; i < rates.length; i++) {
            assertThat(OperatingCharacteristics.allTierNoFail(p, INTERACTIVE, rates[i], 0))
                    .isEqualTo(equivalent[i], org.assertj.core.data.Offset.offset(.0000015));
            assertThat(OperatingCharacteristics.allTierNoFail(p, INTERACTIVE, rates[i], .05))
                    .isEqualTo(plusFive[i], org.assertj.core.data.Offset.offset(.0000015));
            assertThat(OperatingCharacteristics.allTierNoFail(p, INTERACTIVE, rates[i], .10))
                    .isEqualTo(plusTen[i], org.assertj.core.data.Offset.offset(.0000015));
        }
        // The owner's 20% target: holds through the 0-5% parse grid, fails at 6% exactly as before.
        assertThat(1 - OperatingCharacteristics.allTierNoFail(p, INTERACTIVE, .05, 0)).isLessThan(.20);
        assertThat(1 - OperatingCharacteristics.allTierNoFail(p, INTERACTIVE, .06, 0)).isGreaterThan(.20);
        // Paid and Critique are unchanged by the study-tip change.
        assertThat(p.routes.get(PAID).margin()).isEqualTo(5);
        assertThat(p.routes.get(CRITIQUE).margin()).isEqualTo(4);
    }

    @Test void worstCaseT3T2CombinedAndUnionBoundOracle() throws IOException, NoSuchAlgorithmException {
        FrozenGateParameters p = parameters();
        double[] t3Expected = {.12165, .11493, .09671};
        double[] t2Expected = {.04945, .01354, .00869};
        double[] combinedExpected = {.16508, .12692, .10456};
        double[] boundExpected = {.17110, .12848, .10540};
        for (int i = 0; i < ROUTES.size(); i++) {
            String route = ROUTES.get(i);
            int n = p.routes.get(route).workflows().stream()
                    .mapToInt(name -> p.workflows.get(name).primarySize()).sum();
            double t3 = OperatingCharacteristics.t3Fail(n, p.routes.get(route).margin(), .5, .5, 0);
            double t2 = OperatingCharacteristics.t2Fail(p, route, .02, .02, 0);
            assertThat(t3).isCloseTo(t3Expected[i], org.assertj.core.data.Offset.offset(.000005));
            assertThat(t2).isCloseTo(t2Expected[i], org.assertj.core.data.Offset.offset(.000005));
            assertThat(1 - (1 - t3) * (1 - t2))
                    .isCloseTo(combinedExpected[i], org.assertj.core.data.Offset.offset(.000005));
            assertThat(OperatingCharacteristics.unionBound(t3, t2))
                    .isCloseTo(boundExpected[i], org.assertj.core.data.Offset.offset(.000005));
        }
    }

    @Test void allTierTableOracleIncludingReserveT1Exposure() throws IOException, NoSuchAlgorithmException {
        FrozenGateParameters p = parameters();
        double[][][] expected = {
            {{.834915,.873081,.895440},{.119539,.499431,.585672},{.001233,.126638,.186708}},
            {{.834891,.873075,.895436},{.119535,.499426,.585668},{.001233,.126636,.186707}},
            {{.834721,.873028,.895408},{.119504,.499391,.585641},{.001233,.126627,.186698}},
            {{.833381,.872660,.895184},{.119258,.499113,.585432},{.001230,.126553,.186628}},
            {{.811979,.866728,.891572},{.115346,.494648,.582051},{.001187,.125365,.185502}},
            {{.796089,.862249,.888837},{.112460,.491283,.579497},{.001156,.124470,.184651}}
        };
        for (int rate = 0; rate < p.parseRates.size(); rate++)
            for (int delta = 0; delta < p.t2Deltas.size(); delta++)
                for (int route = 0; route < ROUTES.size(); route++) {
                    double actual = OperatingCharacteristics.allTierNoFail(p, ROUTES.get(route),
                            p.parseRates.get(rate), p.t2Deltas.get(delta));
                    assertThat(actual).describedAs("r=%s delta=%s route=%s",
                            p.parseRates.get(rate), p.t2Deltas.get(delta), ROUTES.get(route))
                            .isCloseTo(expected[rate][delta][route], org.assertj.core.data.Offset.offset(.0000005));
                }
    }

    @Test void tierTwoSensitivityGridAndWholeRouteRangesOracle() throws IOException, NoSuchAlgorithmException {
        FrozenGateParameters p = parameters();
        double[] baselineRates = {.01, .02};
        double[] correlations = {0, .5, .8};
        double[] increases = {.05, .10, .15, .20};
        double[][][] publishedTierTwoPass = {
            {{.2479,.0036,.0000,.0000},{.6727,.1910,.0306,.0035},{.7453,.2640,.0554,.0089}},
            {{.3239,.0058,.0000,.0000},{.7270,.2199,.0363,.0042},{.7917,.2975,.0641,.0104}},
            {{.3751,.0076,.0000,.0000},{.7585,.2388,.0402,.0046},{.8180,.3188,.0699,.0114}},
            {{.1361,.0014,.0000,.0000},{.5643,.1431,.0217,.0024},{.6484,.2067,.0415,.0067}},
            {{.2494,.0037,.0000,.0000},{.6738,.1916,.0307,.0035},{.7463,.2648,.0556,.0090}},
            {{.3412,.0064,.0000,.0000},{.7380,.2263,.0376,.0043},{.8010,.3048,.0661,.0107}}
        };
        double[][][] tierTwoRanges = {
            {{.1361,.3751},{.0014,.0076},{0,.0001},{0,.0001}},
            {{.5643,.7585},{.1431,.2388},{.0217,.0402},{.0024,.0046}},
            {{.6484,.8180},{.2067,.3188},{.0415,.0699},{.0067,.0114}}
        };
        double[][][] wholeRouteRanges = {
            {{.1195,.3295},{.0012,.0067},{0,.0001},{0,.0001}},
            {{.4994,.6713},{.1266,.2113},{.0192,.0356},{.0021,.0041}},
            {{.5857,.7389},{.1867,.2880},{.0375,.0632},{.0060,.0103}}
        };
        double[][][] computedTierTwo = new double[ROUTES.size()][increases.length][baselineRates.length * correlations.length];
        double[][][] computedWhole = new double[ROUTES.size()][increases.length][baselineRates.length * correlations.length];
        for (int b = 0; b < baselineRates.length; b++)
            for (int correlation = 0; correlation < correlations.length; correlation++)
                for (int route = 0; route < ROUTES.size(); route++)
                    for (int delta = 0; delta < increases.length; delta++) {
                        String routeName = ROUTES.get(route);
                        double pass = 1 - OperatingCharacteristics.t2Fail(p, routeName,
                                baselineRates[b], baselineRates[b] + increases[delta], correlations[correlation]);
                        int cell = b * correlations.length + correlation;
                        computedTierTwo[route][delta][cell] = pass;
                        int n = p.routes.get(routeName).workflows().stream()
                                .mapToInt(name -> p.workflows.get(name).primarySize()).sum();
                        double t3Pass = 1 - OperatingCharacteristics.t3Fail(n, p.routes.get(routeName).margin(),
                                p.t3WorstP, p.t3WorstP, p.t3WorstRho);
                        computedWhole[route][delta][cell] = pass * t3Pass;
                        assertThat(pass).describedAs("T2 grid b=%s rho=%s route=%s delta=%s",
                                baselineRates[b], correlations[correlation], routeName, increases[delta])
                                .isCloseTo(publishedTierTwoPass[cell][route][delta],
                                        org.assertj.core.data.Offset.offset(.00005));
                    }
        for (int route = 0; route < ROUTES.size(); route++)
            for (int delta = 0; delta < increases.length; delta++) {
                double minT2 = java.util.Arrays.stream(computedTierTwo[route][delta]).min().orElseThrow();
                double maxT2 = java.util.Arrays.stream(computedTierTwo[route][delta]).max().orElseThrow();
                double minWhole = java.util.Arrays.stream(computedWhole[route][delta]).min().orElseThrow();
                double maxWhole = java.util.Arrays.stream(computedWhole[route][delta]).max().orElseThrow();
                assertPublishedRange(minT2, maxT2, tierTwoRanges[route][delta]);
                assertPublishedRange(minWhole, maxWhole, wholeRouteRanges[route][delta]);
            }
    }

    private static void assertPublishedRange(double minimum, double maximum, double[] published) {
        if (published[0] == 0 && published[1] == .0001) {
            assertThat(minimum).isGreaterThanOrEqualTo(0);
            assertThat(maximum).isLessThan(.0001);
        } else {
            assertThat(minimum).isCloseTo(published[0], org.assertj.core.data.Offset.offset(.00005));
            assertThat(maximum).isCloseTo(published[1], org.assertj.core.data.Offset.offset(.00005));
        }
    }

    @Test void o1ConclusionAndOnePercentAtLeastOneOracle() throws IOException, NoSuchAlgorithmException {
        FrozenGateParameters p = parameters();
        assertThat(OperatingCharacteristics.atLeastOne(.01, 162))
                .isCloseTo(.80371, org.assertj.core.data.Offset.offset(.000005));
        assertThat(1 - Math.pow(.05, 1 / 30.0))
                .isCloseTo(.0950, org.assertj.core.data.Offset.offset(.00005));
        double[] expectedAtFive = {.1880, .1333, .1084};
        for (int i = 0; i < ROUTES.size(); i++)
            assertThat(1 - OperatingCharacteristics.allTierNoFail(p, ROUTES.get(i), .05, 0))
                    .isCloseTo(expectedAtFive[i], org.assertj.core.data.Offset.offset(.00005));
        assertThat(1 - OperatingCharacteristics.allTierNoFail(p, INTERACTIVE, .06, 0))
                .isCloseTo(.2039, org.assertj.core.data.Offset.offset(.00005));
        double t3 = OperatingCharacteristics.t3Fail(162, p.routes.get(INTERACTIVE).margin(), .5, .5, 0);
        double t2 = OperatingCharacteristics.t2Fail(p, INTERACTIVE, .02, .02, 0);
        double t1Five = OperatingCharacteristics.t1FailWithReserve(p, INTERACTIVE, .05, 0);
        double t1Six = OperatingCharacteristics.t1FailWithReserve(p, INTERACTIVE, .06, 0);
        assertThat(t1Five).isCloseTo(.02757, org.assertj.core.data.Offset.offset(.000005));
        assertThat(OperatingCharacteristics.unionBound(t3, t2, t1Five))
                .isCloseTo(.1987, org.assertj.core.data.Offset.offset(.00005));
        assertThat(OperatingCharacteristics.unionBound(t3, t2, t1Six))
                .isCloseTo(.2178, org.assertj.core.data.Offset.offset(.00005));
    }

    @Test void monteCarloSeedsReproduceAndStayNearExact() throws IOException, NoSuchAlgorithmException {
        FrozenGateParameters p = parameters();
        double[][][] published = {
            {{.83150,.87110,.89713},{.83207,.87250,.89557},{.81243,.86623,.89193},
             {.79980,.86493,.89107},{.11633,.49803,.59117}},
            {{.83917,.87233,.89453},{.83103,.87420,.89703},{.81183,.86450,.89390},
             {.79647,.86493,.88700},{.11690,.49677,.58753}}
        };
        double[] rates = {0, .01, .05, .06, .01};
        double[] deltas = {0, 0, 0, 0, .05};
        for (int seedIndex = 0; seedIndex < p.monteCarloSeeds.size(); seedIndex++)
            for (int cell = 0; cell < rates.length; cell++)
                for (int routeIndex = 0; routeIndex < ROUTES.size(); routeIndex++) {
                    String route = ROUTES.get(routeIndex);
                    int seed = p.monteCarloSeeds.get(seedIndex);
                    double actual = XorShiftMonteCarlo.noFail(p, route, rates[cell], deltas[cell], seed);
                    double exact = OperatingCharacteristics.allTierNoFail(p, route, rates[cell], deltas[cell]);
                    System.out.printf("MC seed=%d r=%s delta=%s route=%s actual=%.5f published=%.5f exact=%.6f%n",
                            seed, rates[cell], deltas[cell], route, actual, published[seedIndex][cell][routeIndex], exact);
                    assertThat(XorShiftMonteCarlo.noFail(p, route, rates[cell], deltas[cell], seed)).isEqualTo(actual);
                    if (Math.abs(actual - exact) >= .006)
                        System.out.printf("PREREGISTRATION DISCREPANCY: seed=%d r=%s delta=%s route=%s absolute error=%.6f exceeds .006%n",
                                seed, rates[cell], deltas[cell], route, Math.abs(actual - exact));
                }
        assertThat(XorShiftMonteCarlo.noFail(p, INTERACTIVE, 0, 0, p.monteCarloSeeds.get(0)))
                .isEqualTo(25022 / 30000.0);
        assertThat(XorShiftMonteCarlo.noFail(p, INTERACTIVE, 0, 0, p.monteCarloSeeds.get(1)))
                .isEqualTo(25074 / 30000.0);
        XorShiftMonteCarlo.Generator generator = new XorShiftMonteCarlo.Generator(18213);
        assertThat(generator.next()).isEqualTo(.07996426313184202);
        assertThat(generator.next()).isEqualTo(.6738744415342808);
    }

    @Test void declaredHashAndUnknownOrOutOfRangeFieldsAreRejected() throws IOException, NoSuchAlgorithmException {
        FrozenGateParameters p = currentParameters();
        assertThat(p.sha256).hasSize(64);
        Path file = temp.resolve(GateTestFixture.FILE_NAME);
        Files.writeString(file, Files.readString(file) + " ");
        assertThatThrownBy(() -> FrozenGateParameters.load(file))
                .isInstanceOf(FrozenGateParameters.InvalidParameters.class);
    }

    @Test void missingMalformedUnknownAndOutOfRangeParameterFilesAreRejected()
            throws IOException, NoSuchAlgorithmException {
        Path file = temp.resolve(GateTestFixture.FILE_NAME);
        assertThatThrownBy(() -> FrozenGateParameters.load(file))
                .isInstanceOf(FrozenGateParameters.InvalidParameters.class);
        writeAndHash(file, "{");
        assertThatThrownBy(() -> FrozenGateParameters.load(file))
                .isInstanceOf(FrozenGateParameters.InvalidParameters.class);
        ObjectNode tree = (ObjectNode) new ObjectMapper().readTree(Files.readString(GateTestFixture.SOURCE));
        tree.put(UNKNOWN_RULE, 7);
        writeAndHash(file, tree.toString());
        assertThatThrownBy(() -> FrozenGateParameters.load(file))
                .isInstanceOf(FrozenGateParameters.InvalidParameters.class);
        tree.remove(UNKNOWN_RULE);
        tree.put("unusableFraction", 1.2);
        writeAndHash(file, tree.toString());
        assertThatThrownBy(() -> FrozenGateParameters.load(file))
                .isInstanceOf(FrozenGateParameters.InvalidParameters.class);
    }

    private static void writeAndHash(Path file, String content) throws IOException, NoSuchAlgorithmException {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(file, bytes);
        Files.writeString(file.resolveSibling(file.getFileName() + GateTestFixture.SHA_SUFFIX),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
}
