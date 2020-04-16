package io.github.oliviercailloux.minimax;

public enum StrategyType {

	PESSIMISTIC_MAX, PESSIMISTIC_MIN, PESSIMISTIC_AVG, PESSIMISTIC_WEIGHTED_AVG, RANDOM, TWO_PHASES, TAU,
	TWO_PHASES_TAU, TWO_PHASES_RANDOM, PESSIMISTIC_HEURISTIC, TWO_PHASES_HEURISTIC, MINIMAX_MIN_INC;

	@Override
	public String toString() {
		switch (this) {
		case PESSIMISTIC_MAX:
			return "PessimisticMax";
		case PESSIMISTIC_MIN:
			return "Pessimistic Strategy with min aggregation operator";
		case PESSIMISTIC_AVG:
			return "Pessimistic Strategy with avg aggregation operator";
		case PESSIMISTIC_WEIGHTED_AVG:
			return "Pessimistic Strategy with weighted average aggregation operator";
		case PESSIMISTIC_HEURISTIC:
			return "LimitedPess";
		case RANDOM:
			return "Random";
		case TWO_PHASES:
			return "Two Phases Strategy";
		case TAU:
			return "Tau Strategy";
		case TWO_PHASES_TAU:
			return "Two Phases Tau Strategy";
		case TWO_PHASES_RANDOM:
			return "TwoPhasesRandom";
		case TWO_PHASES_HEURISTIC:
			return "TwoPhases";
		case MINIMAX_MIN_INC:
			return "New Strategy";
		default:
			throw new IllegalStateException();
		}
	}

}
