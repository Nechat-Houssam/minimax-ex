package io.github.oliviercailloux.minimax.strategies;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Verify.verify;

import java.util.Comparator;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.j_voting.Alternative;
import io.github.oliviercailloux.j_voting.Voter;
import io.github.oliviercailloux.minimax.elicitation.UpdateablePreferenceKnowledge;
import io.github.oliviercailloux.minimax.elicitation.Question;
import io.github.oliviercailloux.minimax.elicitation.QuestionCommittee;

public class StrategyRandom implements Strategy {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(StrategyRandom.class);

    public static StrategyRandom newInstance(double probabilityCommittee) {
	return new StrategyRandom(probabilityCommittee, false);
    }

    public static StrategyRandom onlyVoters(double probabilityCommittee) {
	return new StrategyRandom(probabilityCommittee, true);
    }

    private final Helper helper;

    private final boolean onlyVoters;

    private double probabilityCommittee;

    private StrategyRandom(double probabilityCommittee, boolean onlyVoters) {
	checkArgument(Double.isFinite(probabilityCommittee));
	checkArgument(probabilityCommittee >= 0d);
	checkArgument(probabilityCommittee <= 1d);
	this.probabilityCommittee = probabilityCommittee;
	helper = Helper.newInstance();
	this.onlyVoters = onlyVoters;
    }

    public void setRandom(Random random) {
	helper.setRandom(random);
    }

    @Override
    public void setKnowledge(UpdateablePreferenceKnowledge knowledge) {
	helper.setKnowledge(knowledge);
    }

    @Override
    public Question nextQuestion() {
	final int m = helper.getAndCheckM();

	final ImmutableSet<Voter> questionableVoters = helper.getQuestionableVoters();
	if (questionableVoters.isEmpty() && onlyVoters) {
	    LOGGER.debug("Asking a dummy question to voter.");
	    final UnmodifiableIterator<Alternative> iterator = helper.getKnowledge().getAlternatives().iterator();
	    return Question.toVoter(helper.getKnowledge().getVoters().iterator().next(), iterator.next(),
		    iterator.next());
	}

	final boolean askVoters;
	if (questionableVoters.isEmpty()) {
	    verify(m > 2);
	    askVoters = false;
	} else {
	    if (m == 2) {
		askVoters = true;
	    } else {
		final double rnd = helper.getRandom().nextDouble();
		askVoters = rnd > probabilityCommittee;
	    }
	}

	final Question question;
	if (askVoters) {
	    final Voter voter = helper.drawFromStrictlyIncreasing(questionableVoters.asList(), Voter.BY_ID);
	    final ImmutableGraph<Alternative> graph = helper.getKnowledge().getPartialPreference(voter)
		    .asTransitiveGraph();
	    final ImmutableSetMultimap<Alternative, Alternative> incomparables = graph.nodes().stream()
		    .collect(ImmutableSetMultimap.flatteningToImmutableSetMultimap(a -> a,
			    a -> Helper.getIncomparables(graph, a)));
	    final Alternative a = helper.sortAndDraw(incomparables.keySet().asList(), Comparator.naturalOrder());
	    final Alternative b = helper.sortAndDraw(incomparables.get(a).asList(), Comparator.naturalOrder());
	    question = Question.toVoter(voter, a, b);
	} else {
	    verify(m >= 3);
	    final int rank = helper.nextInt(m - 2) + 1;
	    final QuestionCommittee qc = helper.getQuestionAboutHalfRange(rank);
	    question = Question.toCommittee(qc);
	}

	return question;
    }
}
