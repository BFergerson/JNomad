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
    private final Map<Statement, String> originalQueryMap

    SourceCodeExtract(NomadExtractor[] extractors) {
        this.extractors = extractors
        queryFoundList = new ArrayList<>()
        parsedQueryList = new ArrayList<>()
        queryExplainResultList =  new ArrayList<>()
        originalQueryMap = new HashMap<>()
    }

    void addQueryFound(String queryFound) {
        queryFoundList.add(queryFound)
    }

    void addParsedQuery(Statement statement, String originalQuery) {
        parsedQueryList.add(statement)
        originalQueryMap.put(statement, originalQuery)
    }

    List<Statement> getParsedQueryList() {
        return parsedQueryList
    }

    public String getStatementOriginalQuery(Statement statement) {
        return originalQueryMap.get(statement)
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
