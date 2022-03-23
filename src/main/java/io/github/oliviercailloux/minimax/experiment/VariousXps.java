package io.github.oliviercailloux.minimax.experiment;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import com.google.common.math.Stats;
import com.google.common.math.StatsAccumulator;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.json.PrintableJsonObject;
import io.github.oliviercailloux.minimax.elicitation.Oracle;
import io.github.oliviercailloux.minimax.elicitation.PreferenceInformation;
import io.github.oliviercailloux.minimax.elicitation.Question;
import io.github.oliviercailloux.minimax.elicitation.UpdateablePreferenceKnowledge;
import io.github.oliviercailloux.minimax.experiment.json.JsonConverter;
import io.github.oliviercailloux.minimax.experiment.other_formats.ToCsv;
import io.github.oliviercailloux.minimax.strategies.Strategy;
import io.github.oliviercailloux.minimax.strategies.StrategyByMmr;
import io.github.oliviercailloux.minimax.strategies.StrategyFactory;
import io.github.oliviercailloux.minimax.strategies.Helper;
import io.github.oliviercailloux.minimax.utils.Generator;

public class VariousXps {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(VariousXps.class);

    public static void main(String[] args) throws Exception {
	final VariousXps variousXps = new VariousXps();

	variousXps.runWithRandomOracles();
//		variousXps.runWithRandomOraclesOneVoter();
//		variousXps.showFinalStats();
//		variousXps.exportOracles(10, 20, 100);
//		variousXps.tiesWithOracle1();
//		variousXps.runWithOracle0();
//		variousXps.analyzeQuestions();
//		variousXps.summarizeXps();
    }

    public void runWithRandomOracles() throws IOException {
	final int m = 5;
	final int n = 10;
	final int k = 60;
	final int nbRuns = 1;

	final ImmutableList.Builder<StrategyFactory> factoriesBuilder = ImmutableList.<StrategyFactory>builder();
	factoriesBuilder.add(StrategyFactory.limitedCommitteeThenVoters(0));
//		final StrategyFactory factory = StrategyFactory.byMmrs(ThreadLocalRandom.current().nextLong(),
//				MmrLottery.MAX_COMPARATOR);
//		final long seed = ThreadLocalRandom.current().nextLong();
//		final StrategyFactory factory = StrategyFactory.limited(seed, MmrLottery.MIN_COMPARATOR, ImmutableList.of());
	factoriesBuilder.add(StrategyFactory.elitist());

	final ImmutableList<Oracle> oracles = Stream
		.generate(() -> Oracle.build(Generator.genProfile(m, n), Generator.genWeightsOne(m))).limit(nbRuns)
		.collect(ImmutableList.toImmutableList());

	for (StrategyFactory factory : factoriesBuilder.build()) {
	    final Runs runs = runs(factory, oracles, k);
	    final Stats stats = runs.getMinimalMaxRegretStats().get(runs.getK());
	    final String descr = Runner.asStringEstimator(stats);
	    LOGGER.info("Got final estimator: {}.", descr);
	}
    }

    public void runWithRandomOraclesOneVoter() {
	final int m = 15;
	final int n = 1;
	final StrategyFactory factory = StrategyFactory.limitedCommitteeThenVoters(0);

	final ImmutableList<Oracle> oracles = Stream
		.generate(
			() -> Oracle.build(Generator.genProfile(m, n), Generator.genWeightsWithUniformDistribution(m)))
		.limit(200)
		// () -> Oracle.build(Generator.genProfile(m, n),
		// Generator.genWeightsGeometric(m))).limit(200)
		.collect(ImmutableList.toImmutableList());
	final int nbRuns = oracles.size();

	LOGGER.info("Started '{}'.", factory.getDescription());
	final StatsAccumulator statsAccumulator = new StatsAccumulator();
	for (int i = 0; i < nbRuns; ++i) {
	    final Oracle oracle = oracles.get(i);
	    final Strategy strategy = factory.get();

	    final UpdateablePreferenceKnowledge knowledge = UpdateablePreferenceKnowledge
		    .given(oracle.getAlternatives(), oracle.getProfile().keySet());
	    strategy.setKnowledge(knowledge);

	    final ImmutableList.Builder<Question> qBuilder = ImmutableList.builder();
	    final ImmutableList.Builder<Long> tBuilder = ImmutableList.builder();
	    while (true) {
		final long startTime = System.currentTimeMillis();
		final Question q = strategy.nextQuestion();
		final PreferenceInformation a = oracle.getPreferenceInformation(q);
		knowledge.update(a);
		if (knowledge.isProfileComplete()) {
		    break;
		}
		LOGGER.debug("Asked {}.", q);
		qBuilder.add(q);
		tBuilder.add(startTime);
	    }
	    final long endTime = System.currentTimeMillis();
	    final Run run = Run.of(oracle, tBuilder.build(), qBuilder.build(), endTime);
	    LOGGER.info("Time (run {}): {}.", i, run.getTotalTime());
	    statsAccumulator.add(run.getK());
	    LOGGER.info("Asked {} questions.", run.getK());
	}
	final Stats stats = statsAccumulator.snapshot();
	LOGGER.info("Stats k: {}.", stats);
    }

    public void runWithOracle0() throws IOException {
	final int m = 6;
	final int n = 6;
	final int k = 50;
	final ImmutableList.Builder<StrategyFactory> factoriesBuilder = ImmutableList.<StrategyFactory>builder();
//		final long seed = ThreadLocalRandom.current().nextLong();
//		factoriesBuilder.add(StrategyFactory.limited(seed, MmrLottery.MIN_COMPARATOR, ImmutableList.of()));
	factoriesBuilder.add(StrategyFactory.limitedCommitteeThenVoters(0));
//		factoriesBuilder.add(StrategyFactory.elitist());
//		for (int qC = 2; qC < k; qC += 2) {
//			final int qV = k - qC;
//			factoriesBuilder.add(StrategyFactory.limitedCommitteeThenVoters(qC));
//			factoriesBuilder.add(StrategyFactory.limitedVotersThenCommittee(qV));
//		}

	final Path json = Path.of("experiments/Oracles/", String.format("Oracles m = %d, n = %d, 100.json", m, n));
	final List<Oracle> oracles = JsonConverter.toOracles(Files.readString(json));
	final Oracle oracle = oracles.get(0);

	for (StrategyFactory factory : factoriesBuilder.build()) {
	    final Runs runs = runs(factory, oracle, k, 50);
	    final Stats stats = runs.getMinimalMaxRegretStats().get(runs.getK());
	    final String descr = Runner.asStringEstimator(stats);
	    LOGGER.info("Got final estimator: {}.", descr);
	}
    }

    public void tiesWithOracle1() throws IOException {
	final int m = 5;
	final int n = 5;
	final int k = 20;
	final long seed = ThreadLocalRandom.current().nextLong();
	final StrategyFactory factory = StrategyFactory.limited(seed, ImmutableList.of());
	// final StrategyFactory factory = StrategyFactory.limited(seed,
	// ImmutableList.of(QuestioningConstraint.of(QuestionType.VOTER_QUESTION,
	// Integer.MAX_VALUE)));
	// final StrategyFactory factory = StrategyFactory.byMmrs(seed,
	// MmrLottery.MAX_COMPARATOR);
	// final StrategyFactory factory = StrategyFactory.random();

	final Path json = Path.of("experiments/Oracles/", String.format("Oracles m = %d, n = %d, 100.json", m, n));
	final List<Oracle> oracles = JsonConverter.toOracles(Files.readString(json));
	final Oracle oracle = oracles.get(0);

	for (int i = 0; i < 5; ++i) {
	    runShowTies((StrategyByMmr) factory.get(), oracle, k);
	}
    }

    public void exportOracles(int m, int n, int count) throws IOException {
	final ImmutableList.Builder<Oracle> builder = ImmutableList.<Oracle>builder();
	for (int i = 0; i < count; ++i) {
	    final Oracle oracle = Oracle.build(Generator.genProfile(m, n),
		    Generator.genWeightsWithUnbalancedDistribution(m));
	    builder.add(oracle);
	}
	final ImmutableList<Oracle> oracles = builder.build();
	final PrintableJsonObject json = JsonConverter.toJson(oracles);
	Files.writeString(Path.of("experiments/Oracles/",
		String.format("Oracles m = %d, n = %d, %d, unbalanced.json", m, n, count)), json.toString());
    }

    public Runs runs(StrategyFactory factory, Oracle oracle, int k, int nbRuns) throws IOException {
	final ImmutableList<Oracle> oracles = Stream.generate(() -> oracle).limit(nbRuns)
		.collect(ImmutableList.toImmutableList());
	return runs(factory, oracles, k);
    }

    public Runs runs(StrategyFactory factory, List<Oracle> oracles, int k) throws IOException {
	return runs(factory, oracles, k, Path.of("experiments/"));
    }

    public Runs runs(StrategyFactory factory, List<Oracle> oracles, int k, Path outDir) throws IOException {
	final int m = oracles.stream().map(Oracle::getM).distinct().collect(MoreCollectors.onlyElement());
	final int n = oracles.stream().map(Oracle::getN).distinct().collect(MoreCollectors.onlyElement());
	final int nbRuns = oracles.size();

	Files.createDirectories(outDir);
	final String prefixDescription = factory.getDescription() + ", m = " + m + ", n = " + n + ", k = " + k;
	final String prefixTemp = prefixDescription + ", ongoing";
	final Path tmpJson = outDir.resolve(prefixTemp + ".json");
	final Path tmpCsv = outDir.resolve(prefixTemp + ".csv");

	final ImmutableList.Builder<Run> runsBuilder = ImmutableList.builder();
	LOGGER.info("Started '{}'.", factory.getDescription());
	for (int i = 0; i < nbRuns; ++i) {
	    final Oracle oracle = oracles.get(i);
	    final Run run = Runner.run(factory, oracle, k);
	    LOGGER.info("Time (run {}): {}.", i, run.getTotalTime());
	    runsBuilder.add(run);
	    final Runs runs = Runs.of(factory, runsBuilder.build());
	    Files.writeString(tmpJson, JsonConverter.toJson(runs).toString());
	    LOGGER.debug("Written json.");
	    Files.writeString(tmpCsv, ToCsv.toCsv(runs, 1));
	    LOGGER.debug("Written csv.");
	}

	final String prefix = prefixDescription + ", nbRuns = " + nbRuns;
	final Path outJson = outDir.resolve(prefix + ".json");
	final Path outCsv = outDir.resolve(prefix + ".csv");
	Files.move(tmpJson, outJson, StandardCopyOption.REPLACE_EXISTING);
	Files.move(tmpCsv, outCsv, StandardCopyOption.REPLACE_EXISTING);

	return Runs.of(factory, runsBuilder.build());
    }

    public void runShowTies(StrategyByMmr strategy, Oracle oracle, int k) {
	LOGGER.info("Running with {}, {}.", oracle, k);
	final UpdateablePreferenceKnowledge knowledge = UpdateablePreferenceKnowledge.given(oracle.getAlternatives(),
		oracle.getProfile().keySet());
	strategy.setKnowledge(knowledge);

	final ImmutableList.Builder<Question> qBuilder = ImmutableList.builder();
	final ImmutableList.Builder<Integer> tiesBuilder = ImmutableList.builder();

	for (int i = 1; i <= k; i++) {
	    final Question q = strategy.nextQuestion();
	    final PreferenceInformation a = oracle.getPreferenceInformation(q);
	    knowledge.update(a);
	    LOGGER.debug("Asked {}.", q);
	    qBuilder.add(q);
	    final int countTies = Helper
		    .getMinimalElements(strategy.getLastQuestions(), strategy.getLotteryComparator()).size();
	    tiesBuilder.add(countTies);
	}

	final ImmutableList<Question> questions = qBuilder.build();
	final ImmutableList<Integer> ties = tiesBuilder.build();
	LOGGER.debug("Questions:{}.", questions);
	LOGGER.info("Ties: {}.", ties);
    }

    public void analyzeQuestions() throws Exception {
	final int m = 10;
	final int n = 20;
	final int k = 500;
	final int nbRuns = 10;
	final Path json = Path.of("experiments", "TableLinearity", String
		.format("Limited MAX, constrained to [], m = %d, n = %d, k = %d, nbRuns = %d.json", m, n, k, nbRuns));
//		final Path json = Path.of("experiments",
//				"Limited MAX, constrained to [], m = 10, n = 20, k = 500, nbRuns = 10.json");
	final Runs runs = JsonConverter.toRuns(Files.readString(json));
	for (Run run : runs.getRuns()) {
	    LOGGER.info("Run: {} qC, {} qV, mmr {}.", run.getNbQCommittee(), run.getNbQVoters(),
		    run.getMinimalMaxRegrets().get(k).getMinimalMaxRegretValue());
	}
	LOGGER.info("Stats nb qc: {}.", Stats.of(runs.getRuns().stream().mapToInt(Run::getNbQCommittee)));
    }

    public void showFinalStats() throws Exception {
	final int m = 6;
	final int n = 6;
	final int k = 30;
	final int nbRuns = 50;
	final Path json = Path.of("experiments",
		String.format("By MMR MAX, m = %d, n = %d, k = %d, nbRuns = %d.json", m, n, k, nbRuns));
	final Runs runs = JsonConverter.toRuns(Files.readString(json));
	LOGGER.info("qst {} , tot {}", runs.getQuestionTimeStats(), runs.getTotalTimeStats());
//		LOGGER.info("Loss after k: {}.", Runner.asStringEstimator(runs.getLossesStats().get(k)));
//		LOGGER.info("MMR after k: {}.", Runner.asStringEstimator(runs.getMinimalMaxRegretStats().get(k)));
    }

    public void summarizeXps() throws Exception {
	final int m = 6;
	final int n = 6;
	final int k = 30;

//		final ImmutableList.Builder<String> inputsBuilder = ImmutableList.builder();
//		inputsBuilder.add(
//				String.format("Limited, constrained to [], m = %d, n = %d, k = %d, nbRuns = %d.json", m, n, k, nbRuns));
//		inputsBuilder.add(String.format("Limited, constrained to [∞v], m = %d, n = %d, k = %d, nbRuns = %d.json", m, n,
//				k, nbRuns));
//		for (int qC = 2; qC < k; qC += 2) {
//			final int qV = k - qC;
//			inputsBuilder
//					.add(String.format("Limited, constrained to [%dc, %dv], m = %d, n = %d, k = %d, nbRuns = %d.json",
//							qC, qV, m, n, k, nbRuns));
//			inputsBuilder
//					.add(String.format("Limited, constrained to [%dv, %dc], m = %d, n = %d, k = %d, nbRuns = %d.json",
//							qV, qC, m, n, k, nbRuns));
//		}
//		final ImmutableList<String> inputs = inputsBuilder.build();
//		for (String input : inputs) {
//			final Path path = Path.of(String.format("experiments/m = %d, n = %d", m, n), input);
//		}

	final Stream<Path> list = Files.list(Path.of("experiments", String.format("m = %d, n = %d", m, n)))
//		final Stream<Path> list = Files.list(Path.of("experiments", "Geometric"))
		.filter(p -> p.getFileName().toString().endsWith(".json"));
	final ImmutableSet<Path> inputPaths = list.collect(ImmutableSet.toImmutableSet());
	checkState(!inputPaths.isEmpty());
	final ImmutableSet<String> inputNames = inputPaths.stream().map(Path::toString)
		.collect(ImmutableSet.toImmutableSet());

	final int smallestLength = inputNames.stream().mapToInt(String::length).min().orElse(0);
	/**
	 * Consider the greatest possible one without counting .json, thus five less, so
	 * that it gets necessarily counted as the suffix (which does not happen
	 * otherwise in case of a single file).
	 */
	final int greatestCommonPrefixLength = IntStream.range(0, smallestLength + 1 - 5)
		.filter(i -> inputNames.stream().map(s -> s.substring(0, i)).distinct().count() == 1).max().getAsInt();
	/**
	 * Subtlety: the prefix could end with part of the suffix, in which case they
	 * might be counted twice. This happens for example if the set of names is {aac,
	 * aacbc} (or just {aac}). In that case, we shouldn’t attempt to substract both
	 * the prefix and the suffix from aac. Thus the max length for the suffix must
	 * be small enough.
	 */
	final int greatestCommonSuffixLength = IntStream.range(0, smallestLength + 1 - greatestCommonPrefixLength)
		.filter(i -> inputNames.stream().map(s -> s.substring(s.length() - i, s.length())).distinct()
			.count() == 1)
		.max().getAsInt();

	final StringWriter output = new StringWriter();
	final CsvWriter writer = new CsvWriter(output, new CsvWriterSettings());
	final ImmutableMap<String, Function<Runs, Stats>> providers = ImmutableMap.of(String.format("MMR @ k = %d", k),
		(Runs r) -> r.getMinimalMaxRegretStats().get(k), String.format("Loss @ k = %d", k),
		(Runs r) -> r.getLossesStats().get(k));
	final ImmutableList<String> headers = Stream.concat(Stream.of("Strategy"), providers.keySet().stream())
		.collect(ImmutableList.toImmutableList());
	writer.writeHeaders(headers);

	for (Path path : inputPaths) {
//			Pattern.compile("Limited, constrained to [(<nbX>2)(<x>c), 28v], m = 6, n = 6, k = 30, nbRuns = 50.json");
	    final Runs runs = JsonConverter.toRuns(Files.readString(path));
	    verify(runs.getK() == k);
	    final String fullFileName = path.toString();
	    writer.addValue("Strategy", fullFileName.substring(greatestCommonPrefixLength,
		    fullFileName.length() - greatestCommonSuffixLength));
	    for (String header : providers.keySet()) {
		final Stats stats = providers.get(header).apply(runs);
		final String estimator = Runner.asStringEstimator(stats);
		writer.addValue(header, estimator);
	    }
	    writer.writeValuesToRow();
	}
	writer.close();

	final @Nullable String first = Iterables.getFirst(inputNames, "");
	final String commonPrefix = first.substring(0, greatestCommonPrefixLength);
	final String commonPrefixLastPart = Path.of(commonPrefix).getFileName().toString();
	final String commonSuffix = first.substring(first.length() - greatestCommonSuffixLength, first.length());
	final Path outputFile = Path.of(String.format("Summary %s%s%s.csv", commonPrefixLastPart, "…", commonSuffix));
	Files.writeString(outputFile, output.toString());
	LOGGER.info("Written {}.", outputFile);
    }
}
