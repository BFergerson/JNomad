package com.codebrig.jnomad.model

import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresExplain
import com.github.javaparser.Range

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryScore {

    private double score
    private String originalQuery
    private String explainedQuery
    private SourceCodeExtract sourceCodeExtract
    private PostgresExplain explain
    private File queryFile
    private Range queryRange

    public QueryScore() {
    }

    double getScore() {
        return score
    }

    void setScore(double score) {
        this.score = score
    }

    String getOriginalQuery() {
        return originalQuery
    }

    void setOriginalQuery(String originalQuery) {
        this.originalQuery = originalQuery
    }

    String getExplainedQuery() {
        return explainedQuery
    }

    void setExplainedQuery(String explainedQuery) {
        this.explainedQuery = explainedQuery
    }

    SourceCodeExtract getSourceCodeExtract() {
        return sourceCodeExtract
    }

    void setSourceCodeExtract(SourceCodeExtract sourceCodeExtract) {
        this.sourceCodeExtract = sourceCodeExtract
    }

    PostgresExplain getExplain() {
        return explain
    }

    void setExplain(PostgresExplain explain) {
        this.explain = explain
    }

    File getQueryFile() {
        return queryFile
    }

    void setQueryFile(File queryFile) {
        this.queryFile = queryFile
    }

    Range getQueryLocation() {
        return queryRange
    }

    void setQueryLocation(Range queryRange) {
        this.queryRange = queryRange
    }

}
