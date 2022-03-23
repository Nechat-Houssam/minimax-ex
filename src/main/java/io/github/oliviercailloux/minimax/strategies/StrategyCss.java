package io.github.oliviercailloux.minimax.strategies;

import static com.google.common.base.Verify.verify;

import java.util.Comparator;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.j_voting.Alternative;
import io.github.oliviercailloux.j_voting.Voter;
import io.github.oliviercailloux.minimax.elicitation.Question;
import io.github.oliviercailloux.minimax.elicitation.QuestionVoter;
import io.github.oliviercailloux.minimax.elicitation.UpdateablePreferenceKnowledge;
import io.github.oliviercailloux.minimax.regret.PairwiseMaxRegret;

public class StrategyCss implements Strategy {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(StrategyCss.class);

    public static StrategyCss newInstance() {
	return new StrategyCss();
    }

    private final Helper helper;

    private StrategyCss() {
	helper = Helper.newInstance();
    }

    @Override
    public void setKnowledge(UpdateablePreferenceKnowledge knowledge) {
	helper.setKnowledge(knowledge);
    }

    public void setRandom(Random random) {
	helper.setRandom(random);
    }

    @Override
    public Question nextQuestion() {
	final ImmutableSetMultimap<Alternative, PairwiseMaxRegret> mmrs = helper.getMinimalMaxRegrets().asMultimap();

	final Alternative xStar = helper.drawFromStrictlyIncreasing(mmrs.keySet().asList(), Comparator.naturalOrder());
	final ImmutableSet<PairwiseMaxRegret> pmrs = mmrs.get(xStar);
	final PairwiseMaxRegret pmr = helper.drawFromStrictlyIncreasing(pmrs.asList(),
		PairwiseMaxRegret.BY_ALTERNATIVES);
	LOGGER.debug("PMR: {}.", pmr);
	final Alternative yBar = pmr.getY();

	if (pmr.getPmrValue() == 0d) {
	    LOGGER.debug("Asking a dummy question to voter.");
	    final UnmodifiableIterator<Alternative> iterator = helper.getKnowledge().getAlternatives().iterator();
	    return Question.toVoter(helper.getKnowledge().getVoters().iterator().next(), iterator.next(),
		    iterator.next());
	}
	verify(!xStar.equals(yBar));

	final ImmutableMap.Builder<QuestionVoter, Integer> questionsAndScoresBuilder = ImmutableMap.builder();
	for (Voter voter : helper.getQuestionableVoters()) {
	    final ImmutableGraph<Alternative> graph = helper.getKnowledge().getPartialPreference(voter)
		    .asTransitiveGraph();
	    if (!graph.adjacentNodes(xStar).contains(yBar)) {
		final QuestionVoter question = QuestionVoter.given(voter, xStar, yBar);
		questionsAndScoresBuilder.put(question, -1);
	    } else {
		for (Alternative candidate : helper.getKnowledge().getAlternatives()) {
		    final Set<Alternative> comparable = graph.adjacentNodes(candidate);
		    final boolean comparableToY = comparable.contains(yBar) || candidate.equals(yBar);
		    final boolean comparableToX = comparable.contains(xStar) || candidate.equals(xStar);
		    final boolean comparableToExactlyOne = comparableToX != comparableToY;
		    /**
		     * We give a sufficiently high bonus that candidates that are comparable to
		     * exactly one of our currently selected alternatives will be adopted in
		     * priority.
		     */
		    final int bonusScore = comparableToExactlyOne ? helper.getKnowledge().getAlternatives().size() * 2
			    : 0;
		    /**
		     * We add each possible questions involving this candidate (that is, one or two
		     * questions); preferring to compare to our currently selected alternatives if
		     * possible.
		     */
		    if (!comparableToX) {
			verify(!candidate.equals(yBar));
			final QuestionVoter question = QuestionVoter.given(voter, xStar, candidate);
			final int score = graph.successors(candidate).size() + 1;
			questionsAndScoresBuilder.put(question, score + bonusScore);
		    }
		    if (!comparableToY) {
			verify(!candidate.equals(xStar));
			final QuestionVoter question = QuestionVoter.given(voter, candidate, yBar);
			final int score = graph.predecessors(candidate).size() + 1;
			questionsAndScoresBuilder.put(question, score + bonusScore);
		    }
		    if (comparableToX && comparableToY) {
			/**
			 * Lower priority to those cases, we’ll try to select another question if
			 * possible.
			 */
		    }
		}
	    }
	}
	final ImmutableMap<QuestionVoter, Integer> questionsAndScores = questionsAndScoresBuilder.build();
	if (questionsAndScores.isEmpty()) {
	    verify(helper.getKnowledge().getAlternatives().size() >= 3);
	    /**
	     * We reach here because for every voter, every alternative is comparable to
	     * both x and y. This may happen even if MMR ≠ 0, and irrespective of the
	     * profile being completely known. Here is an example found in the wild. Voter1:
	     * 1 > 4 > 3 > 2. Voter2: 2 > 1 > {4, 3}. Voter3: 3 > 1 > 4 > 2. Voter4: 2 > 4 >
	     * 3 > 1. No constraints on the weights. PMR(x = 1, y = 2) = 1 using weight (1,
	     * 0, 0, 0). PMR(x = 2, y = 1) = 1/3 using weights (1, 2/3, 1/3, 0). x* = 2,
	     * ybar = 1.
	     *
	     * Note that questioning the voters further will never help reducing MMR in this
	     * example, and I suppose this is always so.
	     */
	    LOGGER.debug("Questioning committee.");
	    return Question.toCommittee(helper.getQuestionAboutWidestRange());
	}

	final int bestScore = questionsAndScores.values().stream().max(Comparator.naturalOrder()).get();
	final ImmutableSet<QuestionVoter> bestQuestions = questionsAndScores.keySet().stream()
		.filter(q -> questionsAndScores.get(q) == bestScore).collect(ImmutableSet.toImmutableSet());
	return Question.toVoter(helper.sortAndDraw(bestQuestions, Comparator.naturalOrder()));
    }

}
