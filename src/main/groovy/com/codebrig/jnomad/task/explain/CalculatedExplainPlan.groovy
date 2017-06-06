package com.codebrig.jnomad.task.explain

import com.codebrig.jnomad.utils.HQLQueryWalker
import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.statement.Statement

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class CalculatedExplainPlan {

    private final ExplainResult explainResult
    private final double value
    private final ExplainPlan explainPlan
    private final Statement statement
    private Expression nomadValue
    private Object parentToNomadValue
    private double costScore

    CalculatedExplainPlan(ExplainResult explainResult, double value, ExplainPlan explainPlan, Statement statement) {
        this.explainResult = explainResult
        this.value = value
        this.explainPlan = explainPlan
        this.statement = statement
    }

    ExplainResult getExplainResult() {
        return explainResult
    }

    double getValue() {
        return value
    }

    ExplainPlan getExplainPlan() {
        return explainPlan
    }

    Statement getStatement() {
        return statement
    }

    Expression getNomadValue() {
        return nomadValue
    }

    Object getParentToNomadValue() {
        return parentToNomadValue
    }

    double getCostScore() {
        return costScore
    }

    void setCostScore(double costScore) {
        this.costScore = costScore
    }

    void determineNomadParent(String nomadId) {
        def walker = new HQLQueryWalker()
        walker.visit(statement)

        def tempValue = null
        walker.statementParsePath.reverseEach {
            if (tempValue == null && it.toString().contains(nomadId)) {
                //todo: other types
                if ("'jnomad-${nomadId}'".toString() == it.toString()) {
                    tempValue = it
                } else if (nomadId.toString() == it.toString()) {
                    tempValue = it
                }
            }
        }

        if (tempValue != null) {
            nomadValue = tempValue
            parentToNomadValue = walker.getParent(nomadValue)
        }
    }

}
