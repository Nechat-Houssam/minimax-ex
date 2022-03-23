package io.github.oliviercailloux.minimax.strategies;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Verify.verify;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.ImmutableGraph;
import com.google.common.math.IntMath;

import io.github.oliviercailloux.j_voting.Alternative;
import io.github.oliviercailloux.j_voting.Voter;
import io.github.oliviercailloux.minimax.elicitation.DelegatingPreferenceKnowledge;
import io.github.oliviercailloux.minimax.elicitation.Question;
import io.github.oliviercailloux.minimax.elicitation.QuestionType;
import io.github.oliviercailloux.minimax.elicitation.QuestionVoter;
import io.github.oliviercailloux.minimax.elicitation.UpdateablePreferenceKnowledge;
import io.github.oliviercailloux.minimax.regret.PairwiseMaxRegret;
import io.github.oliviercailloux.minimax.regret.RegretComputer;

/**
 * <p>
 * Uses the Regret to get the next question.
 * </p>
 * <p>
 * It is possible to constrain this strategy for asking the first q questions
 * only to voters (or only to committee), then the next q' questions only to
 * committee (or to voters), and so on. BUT this constraint will be lifted if it
 * is impossible to satisfy: for example, if the profile is entirely known and
 * the constraint mandates to ask the next question to voters, this strategy
 * will anyway ask a question to the committee.
 * </p>
 **/
public class StrategyByMmr implements Strategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(StrategyByMmr.class);

    private static class QuestioningConstraints {
	public static QuestioningConstraints of(List<QuestioningConstraint> constraints) {
	    return new QuestioningConstraints(constraints);
	}

	private final ImmutableList<QuestioningConstraint> constraints;

	private int asked;

	private QuestioningConstraints(List<QuestioningConstraint> constraints) {
	    if (!constraints.isEmpty()) {
		checkArgument(constraints.stream().limit(constraints.size() - 1).map(QuestioningConstraint::getNumber)
			.noneMatch(n -> n == Integer.MAX_VALUE));
	    }
	    this.constraints = ImmutableList.copyOf(constraints);
	    asked = 0;
	}

	public void next() {
	    ++asked;
	}

	public boolean hasCurrentConstraint() {
	    if (!constraints.isEmpty() && constraints.get(constraints.size() - 1).getNumber() == Integer.MAX_VALUE) {
		return true;
	    }
	    final int nbConstraints = constraints.stream().mapToInt(QuestioningConstraint::getNumber).reduce(0,
		    IntMath::checkedAdd);
	    return asked < nbConstraints;
	}

	public QuestionType getCurrentConstraint() {
	    checkState(hasCurrentConstraint());

	    int skip = asked;
	    final Iterator<QuestioningConstraint> iterator = constraints.iterator();
	    QuestioningConstraint current = null;
	    while (skip >= 0 && iterator.hasNext()) {
		current = iterator.next();
		skip -= current.getNumber();
	    }
	    verify(skip < 0);
	    assert (current != null);
	    return current.getKind();
	}

	public boolean mayAskCommittee() {
	    return hasCurrentConstraint() ? getCurrentConstraint().equals(QuestionType.COMMITTEE_QUESTION) : true;
	}

	public boolean mayAskVoters() {
	    return hasCurrentConstraint() ? getCurrentConstraint().equals(QuestionType.VOTER_QUESTION) : true;
	}

    }

    public static StrategyByMmr build() {
	return build(MmrLottery.MAX_COMPARATOR);
    }

    public static StrategyByMmr build(Comparator<MmrLottery> comparator) {
	return new StrategyByMmr(comparator, false, ImmutableList.of(), 1.1d);
    }

    public static StrategyByMmr build(Comparator<MmrLottery> comparator, List<QuestioningConstraint> constraints) {
	return new StrategyByMmr(comparator, false, constraints, 1.1d);
    }

    public static StrategyByMmr build(Comparator<MmrLottery> comparator, boolean limited,
	    List<QuestioningConstraint> constraints, double penalty) {
	return new StrategyByMmr(comparator, limited, constraints, penalty);
    }

    public static StrategyByMmr limited(Comparator<MmrLottery> comparator, List<QuestioningConstraint> constraints) {
	return build(comparator, true, constraints, 1.1d);
    }

    public static StrategyByMmr limited(List<QuestioningConstraint> constraints) {
	return limited(MmrLottery.MAX_COMPARATOR, constraints);
    }

    private final Helper helper;

    private boolean limited;

    private final QuestioningConstraints constraints;

    private final Comparator<MmrLottery> lotteryComparator;

    private ImmutableMap<Question, MmrLottery> questions;

    private double penalty;

    private StrategyByMmr(Comparator<MmrLottery> lotteryComparator, boolean limited,
	    List<QuestioningConstraint> constraints, double penalty) {
	checkArgument(penalty >= 1d);
	this.lotteryComparator = checkNotNull(lotteryComparator);
	this.limited = limited;
	this.constraints = QuestioningConstraints.of(constraints);
	this.penalty = penalty;

	helper = Helper.newInstance();
	questions = null;
	LOGGER.debug("Creating with constraints: {}.", constraints);
    }

    public Comparator<MmrLottery> getLotteryComparator() {
	return lotteryComparator;
    }

    public void setRandom(Random random) {
	helper.setRandom(random);
    }

    @Override
    public void setKnowledge(UpdateablePreferenceKnowledge knowledge) {
	helper.setKnowledge(knowledge);
    }

    public boolean isLimited() {
	return limited;
    }

    public void setLimited(boolean limited) {
	this.limited = limited;
    }

    @Override
    public Question nextQuestion() {
	final int m = helper.getAndCheckM();
	if (m == 2) {
	    verify(constraints.mayAskVoters());
	}

	final boolean allowCommittee = constraints.mayAskCommittee() && m >= 3;
	final boolean allowVoters = (constraints.mayAskVoters() || m == 2)
		&& !helper.getKnowledge().isProfileComplete();

	LOGGER.debug("Next question, allowing committee? {}; allowing voters? {}.", allowCommittee, allowVoters);

	if (helper.getKnowledge().isProfileComplete() && !allowCommittee) {
	    LOGGER.debug("Asking a dummy question to voter.");
	    final UnmodifiableIterator<Alternative> iterator = helper.getKnowledge().getAlternatives().iterator();
	    return Question.toVoter(helper.getKnowledge().getVoters().iterator().next(), iterator.next(),
		    iterator.next());
	}

	verify(allowCommittee || allowVoters);

	final ImmutableSet.Builder<Question> questionsBuilder = ImmutableSet.builder();

	if (limited) {
	    if (allowVoters) {
		final ImmutableSetMultimap<Alternative, PairwiseMaxRegret> mmrs = helper.getMinimalMaxRegrets()
			.asMultimap();

		final Alternative xStar = helper.drawFromStrictlyIncreasing(mmrs.keySet().asList(),
			Comparator.naturalOrder());
		final ImmutableSet<PairwiseMaxRegret> pmrs = mmrs.get(xStar);
		final PairwiseMaxRegret pmr = helper.drawFromStrictlyIncreasing(pmrs.asList(),
			PairwiseMaxRegret.BY_ALTERNATIVES);
		final Alternative yBar = pmr.getY();
		helper.getQuestionableVoters().stream().map(v -> getLimitedQuestion(xStar, yBar, v))
			.forEach(q -> questionsBuilder.add(Question.toVoter(q)));
	    }

	    if (allowCommittee) {
		IntStream.rangeClosed(1, m - 2).boxed()
			.forEach(i -> questionsBuilder.add(Question.toCommittee(helper.getQuestionAboutHalfRange(i))));
	    }
	} else {
	    if (allowVoters) {
		helper.getPossibleVoterQuestions().stream().forEach(q -> questionsBuilder.add(Question.toVoter(q)));
	    }
	    if (allowCommittee) {
		helper.getQuestionsAboutLambdaRangesWiderThanOrAll(0.1).stream()
			.forEach(q -> questionsBuilder.add(Question.toCommittee(q)));
	    }
	}
	constraints.next();

	questions = questionsBuilder.build().stream().collect(ImmutableMap.toImmutableMap(q -> q, this::toLottery));
	verify(!questions.isEmpty());

	final Comparator<Question> questionsComparator = Comparator.comparing(q -> adjustLottery(q, questions.get(q)),
		lotteryComparator);
	final ImmutableSet<Question> bestQuestions = Helper.getMinimalElements(questions.keySet(),
		questionsComparator);
	final ImmutableMap<Question, MmrLottery> sortedQuestions = questions.keySet().stream()
		.sorted(questionsComparator).collect(ImmutableMap.toImmutableMap(q -> q, questions::get));
	LOGGER.debug("Best questions: {}.", bestQuestions);
	final Question winner = helper.sortAndDraw(bestQuestions.asList(), Comparator.naturalOrder());
	if (winner.getType() == QuestionType.COMMITTEE_QUESTION) {
	    LOGGER.debug("Questioning committee: {}, best lotteries: {}.", winner.asQuestionCommittee(),
		    sortedQuestions.entrySet().stream().limit(6).collect(ImmutableList.toImmutableList()));
	}
	return winner;
    }

    public ImmutableMap<Question, MmrLottery> getLastQuestions() {
	checkState(questions != null);
	return questions;
    }

    private QuestionVoter getLimitedQuestion(Alternative xStar, Alternative yBar, Voter voter) {
	final ImmutableGraph<Alternative> graph = helper.getKnowledge().getPartialPreference(voter).asTransitiveGraph();
	final QuestionVoter question;
	if (!graph.adjacentNodes(xStar).contains(yBar)) {
	    if (xStar.equals(yBar)) {
		verify(helper.getMinimalMaxRegrets().getMinimalMaxRegretValue() == 0d);
		/** We do not care which question we ask. */
		question = helper.getPossibleVoterQuestions().stream().sorted().findFirst().get();
	    } else {
		question = QuestionVoter.given(voter, xStar, yBar);
	    }
	} else {
	    final Alternative tryFirst;
	    final Alternative trySecond;
	    if (graph.hasEdgeConnecting(xStar, yBar)) {
		tryFirst = xStar;
		trySecond = yBar;
	    } else if (graph.hasEdgeConnecting(yBar, xStar)) {
		tryFirst = yBar;
		trySecond = xStar;
	    } else {
		throw new VerifyException(String.valueOf(xStar.equals(yBar))
			+ " Should reach here only when profile is complete or some weights are known to be equal, which we suppose will not happen.");
	    }
	    final Comparator<EndpointPair<Alternative>> comparingPair = Comparator.comparing(EndpointPair::nodeU);
	    final Comparator<EndpointPair<Alternative>> c2 = comparingPair.thenComparing(EndpointPair::nodeV);
	    question = getQuestionAboutIncomparableTo(voter, graph, tryFirst)
		    .or(() -> getQuestionAboutIncomparableTo(voter, graph, trySecond))
		    .orElseGet(() -> getQuestionAbout(voter,
			    helper.sortAndDraw(Helper.getIncomparablePairs(graph).asList(), c2)));
	}
	return question;
    }

    private Optional<QuestionVoter> getQuestionAboutIncomparableTo(Voter voter, Graph<Alternative> graph,
	    Alternative a) {
	final ImmutableSet<Alternative> incomparables = Helper.getIncomparables(graph, a)
		.collect(ImmutableSet.toImmutableSet());
	return incomparables.isEmpty() ? Optional.empty()
		: Optional.of(QuestionVoter.given(voter, a,
			helper.sortAndDraw(incomparables.asList(), Comparator.naturalOrder())));
    }

    private QuestionVoter getQuestionAbout(Voter voter, EndpointPair<Alternative> incomparablePair) {
	return QuestionVoter.given(voter, incomparablePair.nodeU(), incomparablePair.nodeV());
    }

    private MmrLottery toLottery(Question question) {
	final double yesMMR;
	{
	    final DelegatingPreferenceKnowledge delegatingKnowledge = DelegatingPreferenceKnowledge
		    .given(helper.getKnowledge(), question.getPositiveInformation());
	    final RegretComputer rc = new RegretComputer(delegatingKnowledge);
	    yesMMR = rc.getMinimalMaxRegrets().getMinimalMaxRegretValue();
	}

	final double noMMR;
	{
	    final DelegatingPreferenceKnowledge delegatingKnowledge = DelegatingPreferenceKnowledge
		    .given(helper.getKnowledge(), question.getNegativeInformation());
	    final RegretComputer rc = new RegretComputer(delegatingKnowledge);
	    noMMR = rc.getMinimalMaxRegrets().getMinimalMaxRegretValue();
	}
	final MmrLottery lottery = MmrLottery.given(yesMMR, noMMR);
	return lottery;
    }

    private MmrLottery adjustLottery(Question question, MmrLottery lottery) {
	final MmrLottery output;
	switch (question.getType()) {
	case COMMITTEE_QUESTION:
	    if (lottery.getMmrIfYes() <= lottery.getMmrIfNo()) {
		output = MmrLottery.given(lottery.getMmrIfYes() * penalty + 1e-6, lottery.getMmrIfNo());
	    } else {
		output = MmrLottery.given(lottery.getMmrIfYes(), lottery.getMmrIfNo() * penalty + 1e-6);
	    }
	    break;
	case VOTER_QUESTION:
	    output = lottery;
	    break;
	default:
	    throw new VerifyException();
	}
	return output;
    }

}
