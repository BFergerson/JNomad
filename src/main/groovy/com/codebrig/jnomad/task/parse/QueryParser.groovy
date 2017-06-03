package com.codebrig.jnomad.task.parse

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.SourceCodeTypeSolver
import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.parse.rank.ColumnHitMap
import com.codebrig.jnomad.task.parse.rank.TableRank
import com.codebrig.jnomad.task.parse.rank.TableRankVisitor
import com.codebrig.jnomad.utils.CodeLocator
import com.codebrig.jnomad.utils.QueryCleaner
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import net.sf.jsqlparser.parser.CCJSqlParserUtil
import net.sf.jsqlparser.statement.Statement
import net.sf.jsqlparser.statement.delete.Delete
import net.sf.jsqlparser.statement.select.Select
import net.sf.jsqlparser.statement.update.Update
import net.sf.jsqlparser.util.TablesNamesFinder

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryParser {

    class ParseResult {
        List<String> queryList
        SourceCodeExtract extract

        ParseResult(List<String> queryList, SourceCodeExtract extract) {
            this.queryList = queryList
            this.extract = extract
        }
    }

    private QueryEntityAliasMap aliasMap
    private Map<String, ParseResult> staticExprMap
    private Map<String, ParseResult> dynamicExprMap
    private List<ColumnHitMap> columnRankList
    private Map<String, TableRank> tableRankSet
    private List<SourceCodeExtract> scannedFileList
    private SourceCodeTypeSolver typeSolver

    private int parsedQueryCount = 0
    private int failedQueryCount = 0

    public QueryParser(JNomad jNomad) {
        this.scannedFileList = jNomad.scannedFileList
        this.typeSolver = jNomad.getTypeSolver()
    }

    def run() {
        aliasMap = new QueryEntityAliasMap()
        staticExprMap = new HashMap<>()
        dynamicExprMap = new HashMap<>()
        columnRankList = new ArrayList<>()
        tableRankSet = new HashMap<>()

        run(scannedFileList)
    }

    def run(List<SourceCodeExtract> scannedFileList) {
        def map = new HashMap<String, SourceCodeExtract>()
        for (SourceCodeExtract visitor : scannedFileList) {
            //query literals
            def queryExtractor = visitor.queryLiteralExtractor

            for (def queryString : queryExtractor.possibleQueryList) {
                def parseResult = staticExprMap.get(queryString)
                if (parseResult == null) {
                    staticExprMap.put(queryString, parseResult = new ParseResult(new ArrayList<>(), visitor))
                }
                parseResult.queryList.add(queryString)
                visitor.addQueryFound(queryString)
            }
            for (def queryString : queryExtractor.possibleDynamicQueryList) {
                def parseResult = dynamicExprMap.get(queryString)
                if (parseResult == null) {
                    dynamicExprMap.put(queryString, parseResult = new ParseResult(new ArrayList<>(), visitor))
                }
                parseResult.queryList.add(queryString)
                visitor.addQueryFound(queryString)
            }

            //table aliases
            def queryTableAlias = visitor.queryTableAliasExtractor
            if (!queryTableAlias.tableNameAliasMap.isEmpty()) {
                aliasMap.putAllTableNameAlias(queryTableAlias.tableNameAliasMap)
            }

            //column aliases
            def queryColumnAlias = visitor.queryColumnAliasExtractor
            if (!queryColumnAlias.columnNameAliasMap.isEmpty()) {
                map.put(queryColumnAlias.qualifiedClassName, visitor)
                queryColumnAlias.columnNameAliasMap.each {
                    aliasMap.addTableColumnNameAlias(
                            aliasMap.getTableName(queryColumnAlias.className.toLowerCase()) + "." + it.key,
                            it.value)
                }
            }
        }

        //extends thing
        for (SourceCodeExtract visitor : scannedFileList) {
            //column aliases
            def queryColumnAlias = visitor.queryColumnAliasExtractor
            if (!queryColumnAlias.columnNameAliasMap.isEmpty()) {
                def classExtendsPath = queryColumnAlias.extendsClassPath
                if (!classExtendsPath.isEmpty()) {
                    classExtendsPath.each {
                        ClassOrInterfaceDeclaration declaration = CodeLocator.locateClassOrInterfaceDeclaration(typeSolver, it)
                        if (declaration != null) {
                            CompilationUnit unit = CodeLocator.demandCompilationUnit(declaration)
                            def qualifiedName = ""
                            if (unit.package.isPresent()) {
                                qualifiedName = unit.package.get().name.qualifiedName + "." + declaration.name
                            }

                            def value = map.get(qualifiedName)
                            if (value != null) {
                                value.queryColumnAliasExtractor.columnNameAliasMap.each {
                                    aliasMap.addTableColumnNameAlias(
                                            aliasMap.getTableName(queryColumnAlias.className.toLowerCase()) + "." + it.key,
                                            it.value)

                                    //add to extract's column alias extractor
                                    queryColumnAlias.columnNameAliasMap.put(it.key, it.value)
                                    queryColumnAlias.columnNameAliasMap.put(it.value, it.value)
                                }
                            }
                        }
                    }
                }
            }
        }

        //embeddables
        for (SourceCodeExtract visitor : scannedFileList) {
            //column aliases
            def queryColumnAlias = visitor.queryColumnAliasExtractor
            def joinExtractor = visitor.queryColumnJoinExtractor
            if (!joinExtractor.embeddedJoinTableTypeMap.isEmpty()) {
                joinExtractor.embeddedJoinTableTypeMap.each {
                    def embedExtract = CodeLocator.getJPAEmbeddableSourceCodeExtract(scannedFileList, it.value)
                    if (embedExtract != null) {
                        def value = embedExtract.getQueryColumnAliasExtractor()
                        value.columnNameAliasMap.each {
                            aliasMap.addTableColumnNameAlias(
                                    aliasMap.getTableName(queryColumnAlias.className.toLowerCase()) + "." + it.key,
                                    it.value)

                            //add to extract's column alias extractor
                            queryColumnAlias.columnNameAliasMap.put(it.key, it.value)
                            queryColumnAlias.columnNameAliasMap.put(it.value, it.value)
                        }
                    }
                }
            }
        }

        staticExprMap.each {
            parseQuery(it.key, it.value)
        }

        for (TableRank tableRank : tableRankSet.values()) {
            for (def hitMap : tableRank.allColumnHitMaps) {
                columnRankList.add(hitMap)
            }
        }
    }

    private void parseQuery(String originalQuery, ParseResult result) {
        Statement statement
        Select selectStatement
        TablesNamesFinder tablesNamesFinder

        try {
            def cleanSQL = QueryCleaner.cleanQueryParse(originalQuery)
            parsedQueryCount++
            println "Parsing query: " + cleanSQL

            statement = CCJSqlParserUtil.parse(cleanSQL)
            if (statement instanceof Select) {
                selectStatement = (Select) statement
                tablesNamesFinder = new TablesNamesFinder()
                List<String> tableList = tablesNamesFinder.getTableList(selectStatement)

                //get table name (ensure isn't alias)
                def tableName = tableList.get(0).toLowerCase().replace("public.", "") //todo: don't hardcode
                def tableNameAlias = aliasMap.getTableName(tableName)
                if (tableNameAlias != null) {
                    tableName = tableNameAlias
                }

                //get column hit counts
                TableRank tableRank = tableRankSet.get(tableName)
                if (tableRank == null) {
                    tableRank = new TableRank(tableName)
                    tableRankSet.put(tableName, tableRank)
                }

                //column aliases
                aliasMap.columnNameAliasMap.each {
                    if (it.key.startsWith(tableRank.tableName + ".")) {
                        tableRank.addColumnAlias(it.key.replace(tableRank.tableName + ".", ""), it.value)
                    }
                }

                TableRankVisitor rankVisitor = new TableRankVisitor(tableRank, result.queryList.size())
                selectStatement.getSelectBody().accept(rankVisitor)
                result.extract.addParsedQuery(selectStatement, originalQuery)
            } else if (statement instanceof Update) {
                Update updateStatement = (Update) statement
                tablesNamesFinder = new TablesNamesFinder()
                List<String> tableList = tablesNamesFinder.getTableList(updateStatement)

                //get table name (ensure isn't alias)
                def tableName = tableList.get(0).toLowerCase().replace("public.", "") //todo: don't hardcode
                def tableNameAlias = aliasMap.getTableName(tableName)
                if (tableNameAlias != null) {
                    tableName = tableNameAlias
                }

                //get column hit counts
                TableRank tableRank = tableRankSet.get(tableName)
                if (tableRank == null) {
                    tableRank = new TableRank(tableName)
                    tableRankSet.put(tableName, tableRank)
                }

                //column aliases
                aliasMap.columnNameAliasMap.each {
                    if (it.key.startsWith(tableRank.tableName + ".")) {
                        tableRank.addColumnAlias(it.key.replace(tableRank.tableName + ".", ""), it.value)
                    }
                }

                TableRankVisitor rankVisitor = new TableRankVisitor(tableRank, result.queryList.size())
                if (updateStatement.where != null) {
                    updateStatement.where.accept(rankVisitor)
                }
                result.extract.addParsedQuery(updateStatement, originalQuery)
            } else if (statement instanceof Delete) {
                Delete deleteStatement = (Delete) statement
                tablesNamesFinder = new TablesNamesFinder()
                List<String> tableList = tablesNamesFinder.getTableList(deleteStatement)

                //get table name (ensure isn't alias)
                def tableName = tableList.get(0).toLowerCase().replace("public.", "") //todo: don't hardcode
                def tableNameAlias = aliasMap.getTableName(tableName)
                if (tableNameAlias != null) {
                    tableName = tableNameAlias
                }

                //get column hit counts
                TableRank tableRank = tableRankSet.get(tableName)
                if (tableRank == null) {
                    tableRank = new TableRank(tableName)
                    tableRankSet.put(tableName, tableRank)
                }

                //column aliases
                aliasMap.columnNameAliasMap.each {
                    if (it.key.startsWith(tableRank.tableName + ".")) {
                        tableRank.addColumnAlias(it.key.replace(tableRank.tableName + ".", ""), it.value)
                    }
                }

                TableRankVisitor rankVisitor = new TableRankVisitor(tableRank, result.queryList.size())
                if (deleteStatement.where != null) {
                    deleteStatement.where.accept(rankVisitor)
                }
                result.extract.addParsedQuery(deleteStatement, originalQuery)
            } else {
                println "Skipped parsing query: " + statement
                println "\tSource code file: " + result.extract.queryLiteralExtractor.getClassName()
                failedQueryCount++
                parsedQueryCount--
            }
        } catch (Throwable ex) {
            failedQueryCount++
            parsedQueryCount--
            println "Failed to parse query: " + originalQuery.replace("\"", "")
            println "\tSource code file: " + result.extract.queryLiteralExtractor.getClassName()
        }
    }

    int getParsedQueryCount() {
        return parsedQueryCount
    }

    int getFailedQueryCount() {
        return failedQueryCount
    }

    int getTotalStaticQueryCount() {
        int count = 0
        for (ParseResult parseResult : staticExprMap.values()) {
            count += parseResult.queryList.size()
        }
        return count
    }

    int getTotalDynamicQueryCount() {
        int count = 0
        for (ParseResult parseResult : dynamicExprMap.values()) {
            count += parseResult.queryList.size()
        }
        return count
    }

    int getTotalQueryCount() {
        return getTotalStaticQueryCount() + getTotalDynamicQueryCount()
    }

    int getUniqueQueryCount() {
        return getUniqueStaticQueryCount() + getUniqueDynamicQueryCount()
    }

    int getUniqueStaticQueryCount() {
        return staticExprMap.size()
    }

    int getUniqueDynamicQueryCount() {
        return dynamicExprMap.size()
    }

    List<String> getUniqueStaticQueryList() {
        List<String> expressionList = new ArrayList<>()
        staticExprMap.each {
            expressionList.add(it.value.queryList.get(0))
        }
        return expressionList
    }

    List<String> getTotalStaticQueryList() {
        List<String> expressionList = new ArrayList<>()
        staticExprMap.each {
            expressionList.addAll(it.value.queryList)
        }
        return expressionList
    }

    List<String> getUniqueDynamicQueryList() {
        List<String> expressionList = new ArrayList<>()
        dynamicExprMap.each {
            expressionList.add(it.value.queryList.get(0))
        }
        return expressionList
    }

    List<String> getTotalDynamicQueryList() {
        List<String> expressionList = new ArrayList<>()
        dynamicExprMap.each {
            expressionList.addAll(it.value.queryList)
        }
        return expressionList
    }

    List<ColumnHitMap> getColumnRankList() {
        return columnRankList
    }

    QueryEntityAliasMap getAliasMap() {
        return aliasMap
    }

}
