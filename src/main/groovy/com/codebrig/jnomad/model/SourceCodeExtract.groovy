package com.codebrig.jnomad.model

import com.codebrig.jnomad.task.extract.NomadExtractor
import com.codebrig.jnomad.task.extract.extractor.query.*
import com.codebrig.jnomad.task.explain.ExplainResult
import net.sf.jsqlparser.statement.Statement

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class SourceCodeExtract {

    private final NomadExtractor[] extractors
    private final List<String> queryFoundList
    private final List<Statement> parsedQueryList
    private final List<ExplainResult> queryExplainResultList

    SourceCodeExtract(NomadExtractor[] extractors) {
        this.extractors = extractors
        queryFoundList = new ArrayList<>()
        parsedQueryList = new ArrayList<>()
        queryExplainResultList =  new ArrayList<>()
    }

    void addQueryFound(String queryFound) {
        queryFoundList.add(queryFound)
    }

    void addParsedQuery(Statement statement) {
        parsedQueryList.add(statement)
    }

    List<Statement> getParsedQueryList() {
        return parsedQueryList
    }

    void addQueryExplainResult(ExplainResult explainResult) {
        queryExplainResultList.add(explainResult)
    }

    QueryLiteralExtractor getQueryLiteralExtractor() {
        for (NomadExtractor extractor : extractors) {
            if (extractor instanceof QueryLiteralExtractor) {
                return extractor
            }
        }
        return null
    }

    QueryTableAliasExtractor getQueryTableAliasExtractor() {
        for (NomadExtractor extractor : extractors) {
            if (extractor instanceof QueryTableAliasExtractor) {
                return extractor
            }
        }
        return null
    }

    QueryColumnAliasExtractor getQueryColumnAliasExtractor() {
        for (NomadExtractor extractor : extractors) {
            if (extractor instanceof QueryColumnAliasExtractor) {
                return extractor
            }
        }
        return null
    }

    QueryColumnDataTypeExtractor getQueryColumnDataTypeExtractor() {
        for (NomadExtractor extractor : extractors) {
            if (extractor instanceof QueryColumnDataTypeExtractor) {
                return extractor
            }
        }
        return null
    }

    QueryColumnJoinExtractor getQueryColumnJoinExtractor() {
        for (NomadExtractor extractor : extractors) {
            if (extractor instanceof QueryColumnJoinExtractor) {
                return extractor
            }
        }
        return null
    }

    boolean isJPATable() {
        def table = getQueryTableAliasExtractor()
        return !table.tableNameAliasMap.isEmpty()
    }

    boolean isJPAEmbeddable() {
        def table = getQueryTableAliasExtractor()
        return table.embeddableTable
    }

}
