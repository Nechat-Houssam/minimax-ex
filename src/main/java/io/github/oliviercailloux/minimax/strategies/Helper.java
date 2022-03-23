package io.github.oliviercailloux.minimax.strategies;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apfloat.Apint;
import org.apfloat.Aprational;
import org.apfloat.AprationalMath;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMultiset;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;

import io.github.oliviercailloux.j_voting.Alternative;
import io.github.oliviercailloux.j_voting.Voter;
import io.github.oliviercailloux.jlp.elements.SumTerms;
import io.github.oliviercailloux.minimax.elicitation.ConstraintsOnWeights;
import io.github.oliviercailloux.minimax.elicitation.PSRWeights;
import io.github.oliviercailloux.minimax.elicitation.QuestionCommittee;
import io.github.oliviercailloux.minimax.elicitation.QuestionVoter;
import io.github.oliviercailloux.minimax.elicitation.UpdateablePreferenceKnowledge;
import io.github.oliviercailloux.minimax.regret.PairwiseMaxRegret;
import io.github.oliviercailloux.minimax.regret.RegretComputer;
import io.github.oliviercailloux.minimax.regret.Regrets;

public class Helper {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(Helper.class);

    public static ImmutableSet<EndpointPair<Alternative>> getIncomparablePairs(Graph<Alternative> graph) {
	final Set<Alternative> alternatives = graph.nodes();
	final ImmutableSet.Builder<EndpointPair<Alternative>> builder = ImmutableSet.builder();
	for (Alternative a1 : alternatives) {
	    final Stream<Alternative> incomparable = getIncomparables(graph, a1);
	    incomparable.forEach(a2 -> builder.add(EndpointPair.unordered(a1, a2)));
	}
	return builder.build();
    }

    public static Stream<Alternative> getIncomparables(Graph<Alternative> graph, Alternative a1) {
	final Set<Alternative> known = graph.adjacentNodes(a1);
	return graph.nodes().stream().filter(a2 -> !known.contains(a2)).filter(a2 -> !a2.equals(a1));
    }

    public static <K, V extends Comparable<V>> ImmutableSet<K> getMinimalElements(Map<K, ? extends V> elements) {
	return getMinimalElements(elements, Comparator.naturalOrder());
    }

    public static <K, V> ImmutableSet<K> getMinimalElements(Map<K, ? extends V> elements, Comparator<V> comparator) {
	final ImmutableSetMultimap<V, K> keysByValue = elements.keySet().stream()
		.collect(ImmutableSetMultimap.toImmutableSetMultimap(elements::get, k -> k));
	final Optional<V> minValueOpt = keysByValue.keySet().stream().min(comparator);
	final ImmutableSet<K> minKeys = minValueOpt.isEmpty() ? ImmutableSet.of() : keysByValue.get(minValueOpt.get());
	verify(minKeys.isEmpty() == keysByValue.isEmpty());
	return minKeys;
    }

    public static <K> ImmutableSet<K> getMinimalElements(Set<K> elements, Comparator<K> comparator) {
	final ImmutableSet<K> sorted = elements.stream().sorted(comparator).collect(ImmutableSet.toImmutableSet());
	if (sorted.isEmpty()) {
	    return sorted;
	}
	final K min = sorted.iterator().next();
	return sorted.stream().filter(e -> comparator.compare(e, min) == 0).collect(ImmutableSet.toImmutableSet());
    }

    public static <E> boolean isStrictlyIncreasing(Iterable<E> collection, Comparator<E> comparator) {
	final Iterator<E> it = collection.iterator();
	@Nullable
	E e1 = Iterators.getNext(it, null);
	while (it.hasNext()) {
	    final E e2 = it.next();
	    if (comparator.compare(e1, e2) >= 0) {
		return false;
	    }
	    e1 = e2;
	}

	return true;
    }

    public static Helper newInstance() {
	return new Helper();
    }

    private UpdateablePreferenceKnowledge knowledge;

    private Random random;

    private Helper() {
	knowledge = null;
	random = null;
    }

    public UpdateablePreferenceKnowledge getKnowledge() {
	checkState(knowledge != null);
	return knowledge;
    }

    /**
     * @param knowledge must give the alternatives and voters in sorted order
     *
     * @see #drawFromStrictlyIncreasing(List, Comparator)
     */
    public void setKnowledge(UpdateablePreferenceKnowledge knowledge) {
	checkArgument(checkNotNull(knowledge).getAlternatives().size() >= 2);
	checkArgument(isStrictlyIncreasing(knowledge.getAlternatives(), Comparator.naturalOrder()));
	checkArgument(isStrictlyIncreasing(knowledge.getVoters(), Comparator.naturalOrder()));
	this.knowledge = knowledge;
    }

    public int getAndCheckM() {
	final int m = getM();
	verify(m >= 2);
	if (m == 2) {
	    checkState(!getKnowledge().isProfileComplete());
	}
	return m;
    }

    private int getM() {
	return getKnowledge().getAlternatives().size();
    }

    public Random getRandom() {
	if (random == null) {
	    final long seed = ThreadLocalRandom.current().nextLong();
	    LOGGER.debug("Random uses seed {}.", seed);
	    random = new Random(seed);
	}
	return random;
    }

    public void setRandom(Random random) {
	this.random = checkNotNull(random);
    }

    public int nextInt(int size) {
	return getRandom().nextInt(size);
    }

    /**
     * Ordering matters, for stability of the drawing.
     */
    public <E> E drawFromStrictlyIncreasing(List<E> candidates, Comparator<E> comparator) {
	checkArgument(!candidates.isEmpty());
	assert isStrictlyIncreasing(candidates, comparator) : candidates;
	final int i = getRandom().nextInt(candidates.size());
	return candidates.get(i);
    }

    /**
     * @param comparator should be consistent with equals
     */
    public <E> E sortAndDraw(Collection<E> candidates, Comparator<E> comparator) {
	checkArgument(!candidates.isEmpty());
	final int i = getRandom().nextInt(candidates.size());
	final Iterator<E> it = candidates.stream().sorted(comparator).iterator();
	return Iterators.get(it, i);
//		final ArrayList<E> sort = new ArrayList<>(candidates);
//		Collections.sort(sort, comparator);
//		return sort.get(i);
    }

    public ImmutableSet<Voter> getQuestionableVoters() {
	return getKnowledge().getVoters().stream().filter(v -> getKnowledge().getPartialPreference(v)
		.asTransitiveGraph().edges().size() != getM() * (getM() - 1) / 2)
		.collect(ImmutableSet.toImmutableSet());
    }

    public ImmutableSet<QuestionVoter> getPossibleVoterQuestions() {
	return getKnowledge().getVoters().stream().flatMap(this::getPossibleVoterQuestions)
		.collect(ImmutableSet.toImmutableSet());
    }

    private Stream<QuestionVoter> getPossibleVoterQuestions(Voter voter) {
	final Graph<Alternative> graph = knowledge.getProfile().get(voter).asTransitiveGraph();
	final Stream<QuestionVoter> possibleVoterQuestions = getIncomparablePairs(graph).stream()
		.map(p -> QuestionVoter.given(voter, p.nodeU(), p.nodeV()));
	return possibleVoterQuestions;
    }

    private ImmutableSet<Integer> getRanksWithLambdaRangesWiderThan(double threshold) {
	return IntStream.rangeClosed(1, getM() - 2).boxed().filter(r -> getWidthOfLambdaRangeAtRank(r) > threshold)
		.collect(ImmutableSet.toImmutableSet());
    }

    private double getWidthOfLambdaRangeAtRank(int rank) {
	checkArgument(rank >= 1);
	checkArgument(rank <= getM() - 2);
	final Range<Aprational> lambdaRange = getKnowledge().getLambdaRange(rank);
	return (lambdaRange.upperEndpoint().subtract(lambdaRange.lowerEndpoint())).doubleValue();
    }

    public ImmutableSet<QuestionCommittee> getQuestionsAboutLambdaRangesWiderThan(double threshold) {
	return getRanksWithLambdaRangesWiderThan(threshold).stream().map(this::getQuestionAboutHalfRange)
		.collect(ImmutableSet.toImmutableSet());
    }

    /**
     * @return an empty set iff m == 2.
     */
    public ImmutableSet<QuestionCommittee> getQuestionsAboutLambdaRangesWiderThanOrAll(double threshold) {
	final ImmutableSet<QuestionCommittee> questionsThreshold = getQuestionsAboutLambdaRangesWiderThan(threshold);
	final ImmutableSet<QuestionCommittee> questions;
	if (questionsThreshold.isEmpty()) {
	    questions = getQuestionsAboutLambdaRangesWiderThan(0d);
	} else {
	    questions = questionsThreshold;
	}
	if (getM() > 2) {
	    /**
	     * This fails iff the knowledge includes exact points for all lambdas
	     * (equivalently, if all lambda ranges are empty). Considering the way our
	     * experiments are currently designed, we assume this has zero probability.
	     */
	    verify(!questions.isEmpty());
	}
	return questions;
    }

    /**
     * @param rank 1 ≤ rank ≤ m − 2
     */
    public QuestionCommittee getQuestionAboutHalfRange(int rank) {
	final Range<Aprational> lambdaRange = getKnowledge().getLambdaRange(rank);
	final Aprational avg = AprationalMath.sum(lambdaRange.lowerEndpoint(), lambdaRange.upperEndpoint())
		.divide(new Apint(2));
	return QuestionCommittee.given(avg, rank);
    }

    /**
     * @param m ≥ 3
     */
    public QuestionCommittee getQuestionAboutWidestRange() {
	final int maxWidthRank = IntStream.rangeClosed(1, getM() - 2).boxed()
		.max(Comparator.comparing(this::getWidthOfLambdaRangeAtRank)).get();
	return getQuestionAboutHalfRange(maxWidthRank);
    }

    public RegretComputer getRegretComputer() {
	return new RegretComputer(getKnowledge());
    }

    public Regrets getMinimalMaxRegrets() {
	return getRegretComputer().getMinimalMaxRegrets();
    }

    public PSRWeights getMinTauW(PairwiseMaxRegret pmr) {
	final ImmutableSortedMultiset<Integer> multiSetOfRanksOfX = ImmutableSortedMultiset
		.copyOf(pmr.getRanksOfX().values());
	final ImmutableSortedMultiset<Integer> multiSetOfRanksOfY = ImmutableSortedMultiset
		.copyOf(pmr.getRanksOfY().values());

	final RegretComputer regretComputer = getRegretComputer();

	final SumTerms sumTerms = regretComputer.getTermScoreYMinusScoreX(multiSetOfRanksOfY, multiSetOfRanksOfX);
	final ConstraintsOnWeights cow = getKnowledge().getConstraintsOnWeights();
	cow.minimize(sumTerms);
	return cow.getLastSolution();
    }
}
