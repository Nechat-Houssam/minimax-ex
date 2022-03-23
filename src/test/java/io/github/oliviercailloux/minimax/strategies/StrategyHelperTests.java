package io.github.oliviercailloux.minimax.strategies;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.MutableGraph;

import io.github.oliviercailloux.j_voting.Alternative;
import io.github.oliviercailloux.j_voting.Generator;
import io.github.oliviercailloux.j_voting.Voter;
import io.github.oliviercailloux.minimax.elicitation.UpdateablePreferenceKnowledge;

public class StrategyHelperTests {
    @Test
    void testGetIncomparablePairs() throws Exception {
	final ImmutableGraph.Builder<Alternative> builder = GraphBuilder.directed().<Alternative>immutable();
	final Alternative a1 = Alternative.withId(1);
	final Alternative a2 = Alternative.withId(2);
	final Alternative a3 = Alternative.withId(3);
	final Alternative a4 = Alternative.withId(4);
	builder.putEdge(a1, a2);
	builder.putEdge(a1, a3);
	builder.putEdge(a2, a4);
	builder.putEdge(a3, a4);
	builder.putEdge(a1, a4);
	final ImmutableGraph<Alternative> graph = builder.build();
	assertEquals(ImmutableSet.of(EndpointPair.unordered(a2, a3)), Helper.getIncomparablePairs(graph));
    }

    @Test
    void testGetVoters() {
	final UpdateablePreferenceKnowledge k = UpdateablePreferenceKnowledge.given(Generator.getAlternatives(4),
		Generator.getVoters(2));
	final MutableGraph<Alternative> g1 = k.getProfile().get(Voter.withId(1)).asGraph();
	g1.putEdge(Alternative.withId(1), Alternative.withId(2));
	g1.putEdge(Alternative.withId(2), Alternative.withId(3));
	g1.putEdge(Alternative.withId(3), Alternative.withId(4));

	final MutableGraph<Alternative> g2 = k.getProfile().get(Voter.withId(2)).asGraph();
	g2.putEdge(Alternative.withId(1), Alternative.withId(2));
	g2.putEdge(Alternative.withId(2), Alternative.withId(3));
	g2.putEdge(Alternative.withId(3), Alternative.withId(4));

	final Helper helper = Helper.newInstance();
	helper.setKnowledge(k);
	assertEquals(ImmutableSet.of(), helper.getQuestionableVoters());
    }
}
