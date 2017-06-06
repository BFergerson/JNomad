package com.codebrig.jnomad.task.explain

import com.codebrig.jnomad.model.SourceCodeExtract
import com.fasterxml.jackson.annotation.JsonIgnore
import net.sf.jsqlparser.statement.Statement

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
abstract class ExplainResult {

    @JsonIgnore
    private transient String originalQuery
    @JsonIgnore
    private transient String finalQuery
    @JsonIgnore
    private transient Statement explainedStatement
    @JsonIgnore
    private transient SourceCodeExtract sourceCodeExtract

    void setOriginalQuery(String originalQuery) {
        this.originalQuery = Objects.requireNonNull(originalQuery)
    }

    String getOriginalQuery() {
        return originalQuery
    }

    void setFinalQuery(String finalQuery) {
        this.finalQuery = Objects.requireNonNull(finalQuery)
    }

    String getFinalQuery() {
        return finalQuery
    }

    void setExplainedStatement(Statement explainedStatement) {
        this.explainedStatement = Objects.requireNonNull(explainedStatement)
    }

    Statement getExplainedStatement() {
        return explainedStatement
    }

    void setSourceCodeExtract(SourceCodeExtract sourceCodeExtract) {
        this.sourceCodeExtract = Objects.requireNonNull(sourceCodeExtract)
    }

    SourceCodeExtract getSourceCodeExtract() {
        return sourceCodeExtract
    }

    abstract CalculatedExplainPlan calculateCostliestNode(List<String> excludedNodeTypeList)

    abstract CalculatedExplainPlan calculateSlowestNode(List<String> excludedNodeTypeList)

}
