package io.github.oliviercailloux.minimax.experiment;

import static com.google.common.base.Verify.verify;
import static com.google.common.base.Verify.verifyNotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.Graphs;
import com.google.common.graph.ImmutableGraph;

import io.github.oliviercailloux.j_voting.Alternative;
import io.github.oliviercailloux.j_voting.Generator;
import io.github.oliviercailloux.j_voting.Voter;
import io.github.oliviercailloux.j_voting.VoterPartialPreference;
import io.github.oliviercailloux.j_voting.VoterStrictPreference;
import io.github.oliviercailloux.j_voting.preferences.classes.ImmutableLinearPreferenceImpl;
import io.github.oliviercailloux.minimax.elicitation.QuestionVoter;
import io.github.oliviercailloux.minimax.elicitation.VoterPreferenceInformation;
import io.github.oliviercailloux.minimax.strategies.Helper;

public class CompletionXp {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LoggerFactory.getLogger(CompletionXp.class);

    public static void main(String[] args) {
	new CompletionXp().elicitRandomly();
    }

    public void elicitRandomly() {
	final Random random = new Random();
	final ImmutableSet<Alternative> alternatives = Generator.getAlternatives(5);
	final ArrayList<Alternative> shuffled = new ArrayList<>(alternatives.asList());
	Collections.shuffle(shuffled, random);
	final VoterStrictPreference oracle = VoterStrictPreference.given(Voter.ZERO, shuffled);

	final VoterPartialPreference knowledge = VoterPartialPreference.about(oracle.getVoter(), alternatives);
	verify(Helper.isStrictlyIncreasing(alternatives, Comparator.naturalOrder()));
	for (int i = 0; i <= 100; ++i) {
	    final ImmutableGraph<Alternative> graph = knowledge.asTransitiveGraph();
	    final int edgesKnown = graph.edges().size();
	    LOGGER.info("Known: {}.", edgesKnown);
	    final int questionsKnown = edgesKnown;
	    final int n = knowledge.asGraph().nodes().size();
	    final int edgesComplete = n * (n - 1);
	    final int questionsComplete = n * (n - 1) / 2;
	    final int edgesAvailable = edgesComplete - edgesKnown * 2;
	    final int questionsAvailable = questionsComplete - questionsKnown;
	    verify(edgesAvailable == questionsAvailable * 2);
	    if (edgesAvailable == 0) {
		break;
	    }
	    final int questionIndex = random.nextInt(questionsAvailable);
	    int questionOffset = questionIndex;
	    QuestionVoter question = null;
	    for (Alternative a1 : alternatives) {
		final ImmutableSet<Alternative> greaterAlternatives = alternatives.stream()
			.filter(a -> Comparator.<Alternative>naturalOrder().compare(a, a1) > 0)
			.collect(ImmutableSet.toImmutableSet());
		LOGGER.debug("Considering {}, {}.", a1, greaterAlternatives);
		for (Alternative a2 : greaterAlternatives) {
		    if (!(graph.hasEdgeConnecting(a1, a2) || graph.hasEdgeConnecting(a2, a1))) {
			if (questionOffset == 0) {
			    question = QuestionVoter.given(oracle.getVoter(), a1, a2);
			    break;
			}
			--questionOffset;
		    }
		}
		if (question != null) {
		    break;
//				final Set<Alternative> adjacentNodes = knowledge.asTransitiveGraph().adjacentNodes(alternative);
//				final int questionsTaken = (int) adjacentNodes.stream()
//						.filter(a -> Comparator.<Alternative>naturalOrder().compare(alternative, a) < 0).count();
//				final int questionsFreeWithThisAlternative = questionsTaken;
//				if(questionIndex >= questionsFreeWithThisAlternative) {
//					questionOffset -= questionsTaken;
//				}
		}
	    }
	    verifyNotNull(question);
	    final VoterPreferenceInformation answer = oracle.askQuestion(question);
	    LOGGER.info("Obtained {}.", answer);
	    knowledge.asGraph().putEdge(answer.getBetterAlternative(), answer.getWorstAlternative());
	    knowledge.setGraphChanged();
	}

	verify(ImmutableLinearPreferenceImpl.given(oracle.getAlternatives()).asGraph()
		.equals(Graphs.transitiveClosure(knowledge.asGraph())));
    }
}
