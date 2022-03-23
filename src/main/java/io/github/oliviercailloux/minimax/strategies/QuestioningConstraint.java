package io.github.oliviercailloux.minimax.strategies;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.json.bind.annotation.JsonbCreator;
import javax.json.bind.annotation.JsonbProperty;
import javax.json.bind.annotation.JsonbPropertyOrder;

import com.google.common.base.MoreObjects;

import io.github.oliviercailloux.minimax.elicitation.QuestionType;

@JsonbPropertyOrder({ "kind", "number" })
public class QuestioningConstraint {
    @JsonbCreator
    public static QuestioningConstraint of(@JsonbProperty("kind") QuestionType kind,
	    @JsonbProperty("number") int number) {
	return new QuestioningConstraint(kind, number);
    }

    private final QuestionType kind;

    private final int number;

    private QuestioningConstraint(QuestionType kind, int number) {
	this.kind = checkNotNull(kind);
	checkArgument(number >= 1);
	this.number = number;
    }

    public QuestionType getKind() {
	return kind;// This should be: return kind;
    }

    public int getNumber() {
	return number;
    }

    @Override
    public String toString() {
	return MoreObjects.toStringHelper(this).add("kind", kind)
		.add("number", number == Integer.MAX_VALUE ? "∞" : number).toString();
    }
}