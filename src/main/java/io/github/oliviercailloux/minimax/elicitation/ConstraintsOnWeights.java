package io.github.oliviercailloux.minimax.elicitation;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.util.LinkedList;
import java.util.List;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;

import io.github.oliviercailloux.jlp.elements.ComparisonOperator;
import io.github.oliviercailloux.jlp.elements.Constraint;
import io.github.oliviercailloux.jlp.elements.Objective;
import io.github.oliviercailloux.jlp.elements.RangeOfDouble;
import io.github.oliviercailloux.jlp.elements.Sense;
import io.github.oliviercailloux.jlp.elements.SumTerms;
import io.github.oliviercailloux.jlp.elements.SumTermsBuilder;
import io.github.oliviercailloux.jlp.elements.Term;
import io.github.oliviercailloux.jlp.elements.Variable;
import io.github.oliviercailloux.jlp.elements.VariableDomain;
import io.github.oliviercailloux.jlp.mp.IMP;
import io.github.oliviercailloux.jlp.mp.MP;
import io.github.oliviercailloux.jlp.mp.MPBuilder;
import io.github.oliviercailloux.jlp.or_tools.OrToolsSolver;
import io.github.oliviercailloux.jlp.result.Result;
import io.github.oliviercailloux.jlp.result.ResultStatus;
import io.github.oliviercailloux.jlp.result.Solution;
import io.github.oliviercailloux.jlp.solve.Solver;

/**
 *
 * The weight of rank 1 is 1. If there is more than one rank, the weight of
 * lowest rank is 0.
 *
 * Because of imprecision in linear programming optimization, we could end up
 * with weights that are non convex up to a very minor error. This is solved by
 * adding a small epsilon ({@link ConstraintsOnWeights#EPSILON}) to the
 * constraints.
 *
 * <p>
 * The use of this added epsilon modifies the limits admitted by any “feasible”
 * set of weights. This modification may become non negligible when the rank is
 * high. To understand this, consider this example with 6 ranks and minimizing
 * w2 (thus minimizing w3, …, w6). Setting w5 = 0, the difference between w5 and
 * w6 is zero. Because the difference between w4 and w5 must be at least epsilon
 * higher than the difference between w5 and w6, the difference between w4 and
 * w5 must be at least epsilon. Thus, w4 ≥ epsilon. By the same reasoning, the
 * difference between w3 and w4 must be at least two epsilon. Thus, w3 ≥ 3
 * epsilon. Finally, the difference between w2 and w3 must be at least three
 * epsilon. Thus, w2 ≥ 6 epsilon.
 * </p>
 * <p>
 * In general, the lower bound on w2 is computed as follows. w2 ≥ epsilon + 2
 * epsilon + 3 epsilon + … + (m − 3) epsilon = (m² − 5m + 6)/2 epsilon.
 * </p>
 * <p>
 * For m = 20, we obtain w2 ≥ 153 epsilon.
 * </p>
 * <p>
 * This will be a problem in the following circumstance. Assume m = 20. It could
 * be that w2 = 0 is a feasible solution for a given problem in reality, but
 * this program does not find any feasible solution because w2 < 153 epsilon for
 * all (really) feasible solutions. However, considering the kind of constraints
 * accepted by this class, it is difficult to find a problematic example of that
 * kind.
 * </p>
 * <p>
 * Another problem may happen when the coefficients (representing typically the
 * number of voters) are high. Consider the objective w1 − 10^4 w2, to be
 * maximized. Setting w2 to zero would lead to a value of 1d, but because w2 ≥
 * 153 epsilon (if m = 20), with epsilon = 10^−6, we obtain a maximal value of
 * −0.5.
 * </p>
 *
 *
 * @author Olivier Cailloux
 *
 */
public class ConstraintsOnWeights {
    /**
     * TODO Its value must be at least the tolerance that the solver admits.
     */
    public static final double EPSILON = 1e-6;

    /**
     * @param m at least one: the number of ranks, or equivalently, the number of
     *          alternatives.
     */
    public static ConstraintsOnWeights withRankNumber(int m) {
	return new ConstraintsOnWeights(m);
    }

    public static ConstraintsOnWeights copyOf(ConstraintsOnWeights cw) {
	ConstraintsOnWeights c = new ConstraintsOnWeights(cw.builder, cw.convexityConstraintSet);
	return c;
    }

    private MPBuilder builder;

    private final Solver solver;

    private Solution lastSolution;

    private boolean convexityConstraintSet;

    private ConstraintsOnWeights(int m) {
	checkArgument(m >= 1);
	builder = MP.builder();
	builder.addVariable(
		Variable.of("w", VariableDomain.REAL_DOMAIN, RangeOfDouble.closed(1d, 1d), ImmutableSet.of(1)));
	for (int rank = 2; rank < m; ++rank) {
	    builder.addVariable(
		    Variable.of("w", VariableDomain.REAL_DOMAIN, RangeOfDouble.ZERO_ONE_RANGE, ImmutableSet.of(rank)));
	}
	if (m >= 2) {
	    builder.addVariable(
		    Variable.of("w", VariableDomain.REAL_DOMAIN, RangeOfDouble.closed(0d, 0d), ImmutableSet.of(m)));
	}
	solver = new OrToolsSolver();
	lastSolution = null;
	convexityConstraintSet = false;
    }

    /**
     * Copy constructor.
     *
     * @param mp                     should come from another COW instance (to
     *                               guarantee that the structure conforms to
     *                               expectations).
     * @param convexityConstraintSet should come from the same instance (to
     *                               guarantee coherence).
     */
    private ConstraintsOnWeights(MPBuilder mp, boolean convexityConstraintSet) {
	builder = mp; // Replace by: builder = mp;
	solver = new OrToolsSolver();
	lastSolution = null;
	this.convexityConstraintSet = convexityConstraintSet;
    }

    /**
     * May be called only once.
     */
    public void setConvexityConstraint() {
	checkState(!convexityConstraintSet);
	for (int rank = 1; rank <= getM() - 2; ++rank) {
	    builder.addConstraint(Constraint.of("Convexity rank " + rank,
		    SumTerms.of(1d, getVariable(rank), -2d, getVariable(rank + 1), 1d, getVariable(rank + 2)),
		    ComparisonOperator.GE, EPSILON));
	}
	convexityConstraintSet = true;
    }

    /**
     * Adds the constraint: (w_i − w_{i+1}) OP λ (w_{i+1} − w_{i+2}).
     *
     * @param i      1 ≤ i ≤ m-2.
     * @param op     the operator.
     * @param lambda a finite double.
     */
    void addConstraint(int i, ComparisonOperator op, double lambda) {
	checkArgument(i >= 1);
	checkArgument(i <= getM() - 2);
	checkArgument(Double.isFinite(lambda));

	final SumTermsBuilder sumBuilder = SumTerms.builder();
	sumBuilder.addTerm(1, getVariable(i));
	sumBuilder.addTerm(-lambda - 1d, getVariable(i + 1));
	sumBuilder.addTerm(lambda, getVariable(i + 2));
	final Constraint cst = Constraint.of(sumBuilder.build(), op, 0d);
	builder.addConstraint(cst);
    }

    /**
     * @return at least one.
     */
    public int getM() {
	return builder.getVariables().size();
    }

    public Range<Double> getWeightRange(int rank) {
	checkArgument(rank >= 1);
	checkArgument(rank <= getM());

	return boundObjective(SumTerms.of(1d, getVariable(rank)));
    }

    public Term getTerm(double coefficient, int rank) {
	return Term.of(coefficient, getVariable(rank));
    }

    public double maximize(SumTerms sum) {
	final Objective obj = Objective.max(sum);
	return optimize(obj);
    }

    public double minimize(SumTerms sum) {
	final Objective obj;
	if (sum.size() == 0) {
	    obj = Objective.ZERO;
	} else {
	    obj = Objective.min(sum);
	}
	return optimize(obj);
    }

    public PSRWeights getLastSolution() {
	/** PSRWeights only accept convex weights. */
	checkState(convexityConstraintSet);
	final List<Double> weights = new LinkedList<>();
	for (int r = 1; r <= getM(); ++r) {
	    final double value = lastSolution.getValue(getVariable(r));
	    weights.add(value);
	}
	return PSRWeights.given(weights);
    }

    private Variable getVariable(int rank) {
	checkArgument(rank >= 1);
	checkArgument(rank <= getM());
	return builder.getVariable(getVariableDescription(rank));
    }

    private String getVariableDescription(int rank) {
	return Variable.getDefaultDescription("w", ImmutableList.of(rank));
    }

    private double optimize(Objective obj) {
	builder.setObjective(obj);
	final Result result = solver.solve(builder);
	checkArgument(result.getResultStatus().equals(ResultStatus.OPTIMAL));
	lastSolution = result.getSolution().get();
	return lastSolution.getObjectiveValue();
    }

    private double bound(IMP mp) {
	final double bound;

	final Result result = solver.solve(mp);
	switch (result.getResultStatus()) {
	case INFEASIBLE:
	case MEMORY_LIMIT_REACHED:
	case TIME_LIMIT_REACHED:
	    throw new IllegalStateException();
	case UNBOUNDED:
	    if (mp.getObjective().getSense() == Sense.MAX) {
		bound = Double.POSITIVE_INFINITY;
	    } else {
		bound = Double.NEGATIVE_INFINITY;
	    }
	    break;
	case OPTIMAL:
	    bound = result.getSolution().get().getObjectiveValue();
	    break;
	default:
	    throw new AssertionError();
	}
	return bound;
    }

    private Range<Double> boundObjective(SumTerms objectiveFunction) {
	builder.setObjective(Objective.min(objectiveFunction));
	final double lBound = bound(builder);

	builder.setObjective(Objective.max(objectiveFunction));
	final double uBound = bound(builder);

	return RangeOfDouble.using(lBound, uBound);
    }

    public String rangesAsString() {
	StringBuilder sb = new StringBuilder();
	for (int i = 1; i <= builder.getVariables().size(); i++) {
	    sb.append("Rank " + i + " ");
	    sb.append(getWeightRange(i).toString());
	    sb.append("\n");
	}
	return sb.toString();
    }

    @Override
    public String toString() {
	return MoreObjects.toStringHelper(this).add("Builder", builder).toString();
    }
}
