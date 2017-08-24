package com.codebrig.jnomad.task.explain.adapter.postgres

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.model.SourceCodeIndexReport
import com.codebrig.jnomad.task.parse.QueryEntityAliasMap
import com.codebrig.jnomad.task.explain.QueryIndexReport
import com.codebrig.jnomad.task.explain.transform.hql.*
import com.codebrig.jnomad.utils.CodeLocator
import com.codebrig.jnomad.utils.QueryCleaner
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import net.sf.jsqlparser.statement.Statement
import org.postgresql.util.PSQLException

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSetMetaData
import java.util.regex.Pattern

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class PostgresQueryReport extends QueryIndexReport {

    private QueryEntityAliasMap aliasMap
    private PostgresDatabaseDataType databaseDataType

    private Map columnJoinMap = new HashMap<>()
    private Map<String, SourceCodeExtract> sourceCodeExtractMap = new HashMap<>()
    private Map<String, PostgresExplain> postgresExplainMap = new HashMap<>()
    private Map<String, String> failedQueryReasonMap = new HashMap<>()

    PostgresQueryReport(JNomad jNomad, PostgresDatabaseDataType databaseDataType, QueryEntityAliasMap aliasMap) {
        super(jNomad)
        this.databaseDataType = Objects.requireNonNull(databaseDataType)
        this.aliasMap = Objects.requireNonNull(aliasMap)
    }

    @Override
    SourceCodeIndexReport createSourceCodeIndexReport(List<SourceCodeExtract> scannedFileList) {
        List<Connection> connectionList = new ArrayList<>()
        try {
            Class.forName("org.postgresql.Driver")
            for (int i = 0; i < jNomad.dbDatabase.size(); i++) {
                def host = jNomad.dbHost.get(i)
                if (!host.contains(":")) {
                    host += ":5432"
                }
                def connUrl = "jdbc:postgresql://" + host + "/" + jNomad.dbDatabase.get(i)
                def connection = DriverManager.getConnection(connUrl, jNomad.dbUsername.get(i), jNomad.dbPassword.get(i))
                connectionList.add(connection)
            }
        } catch (Exception ex) {
            ex.printStackTrace()
            System.err.println(ex.getClass().getName() + ": " + ex.getMessage())
            System.exit(0)
        }
        return createSourceCodeIndexReport(scannedFileList, connectionList.toArray(new Connection[0]))
    }

    @Override
    SourceCodeIndexReport createSourceCodeIndexReport(List<SourceCodeExtract> scannedFileList, Connection... connections) {
        if (connections == null || connections.length == 0) {
            throw new RuntimeException("Postgres database access was not provided!")
        }

        List<Connection> connectionList = Arrays.asList(connections)
        try {
            resolveColumnDataTypes(scannedFileList)
            for (SourceCodeExtract extract : scannedFileList) {
                for (Statement statement : extract.parsedQueryList) {
                    allQueryList.add(statement.toString())
                    def originalQuery = Objects.requireNonNull(extract.getStatementOriginalQuery(statement))
                    sourceCodeExtractMap.put(originalQuery, extract)

                    def query
                    try {
                        statement.accept(new HQLTableNameTransformer(this))
                        statement.accept(new HQLColumnNameTransformer(this))
                        statement.accept(new HQLTableJoinTransformer(this))
                        statement.accept(new HQLColumnNameTransformer(this)) //fix column names again after joins added
                        statement.accept(new HQLColumnValueTransformer(this))

                        query = QueryCleaner.cleanQueryExecute(statement.toString())
                    } catch (HQLTransformException ex) {
                        println ex.getMessage()
                        println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                        failedQueryParseList.add(originalQuery)
                        failedQueryReasonMap.put(originalQuery, ex.message)
                        continue
                    } catch (Exception ex) {
                        ex.printStackTrace()
                        println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                        failedQueryParseList.add(originalQuery)
                        failedQueryReasonMap.put(originalQuery, ex.message)
                        continue
                    }

                    for (int i = 0; i < connectionList.size(); i++) {
                        def connection = connectionList.get(i)
                        try {
                            explainQuery(connection, query, originalQuery, extract, statement)
                            break
                        } catch (PSQLException ex) {
                            if (ex.serverErrorMessage.routine == "errorMissingColumn") {
                                if ((i + 1) < connectionList.size()) {
                                    continue //try next connection
                                }

                                failedQueryParseList.add(originalQuery)
                                failedQueryReasonMap.put(originalQuery, ex.serverErrorMessage.message)

                                missingRequiredColumnList.add(originalQuery)
                                println "Missing required columns for query: " + query + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.serverErrorMessage.message
                                println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                            } else if (ex.serverErrorMessage.routine == "parserOpenTable") {
                                if ((i + 1) < connectionList.size()) {
                                    continue //try next connection
                                }

                                failedQueryParseList.add(originalQuery)
                                failedQueryReasonMap.put(originalQuery, ex.serverErrorMessage.message)

                                missingRequiredTableList.add(originalQuery)
                                println "Missing required tables for query: " + query + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.serverErrorMessage.message
                                println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                            } else if (ex.serverErrorMessage.routine == "aclcheck_error") {
                                failedQueryParseList.add(originalQuery)
                                failedQueryReasonMap.put(originalQuery, ex.serverErrorMessage.message)

                                permissionDeniedTableList.add(originalQuery)
                                println "Permission denied for query: " + query + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.serverErrorMessage.message
                                println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                            } else {
                                failedQueryParseList.add(originalQuery)
                                failedQueryReasonMap.put(originalQuery, ex.serverErrorMessage.message)
                                println "Failed to execute explain on query: " + statement.toString() + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.serverErrorMessage.message
                                println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace()
                            failedQueryParseList.add(originalQuery)
                            failedQueryReasonMap.put(originalQuery, ex.message)
                            println "Failed to execute explain on query: " + statement.toString() + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.message
                            println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                        }
                    }
                }
            }
        } finally {
            if (connectionList != null) {
                connectionList.each {
                    if (it != null) it.close()
                }
            }
        }

        def indexReport = new SourceCodeIndexReport()
        postgresExplainMap.each {
            if (it.value.plan.totalCost != null) {
                indexReport.addTotalCost(it.value.plan.totalCost, it.value)
            }
            if (it.value.plan.startupCost != null) {
                indexReport.addStartupCost(it.value.plan.startupCost, it.value)
            }
            if (it.value.totalRuntime != null) {
                indexReport.addTotalRuntime(it.value.totalRuntime, it.value)
            }
            if (it.value.executionTime != null) {
                indexReport.addExecutionTime(it.value.executionTime, it.value)
            }

            //seq scan
            if (it.value.plan.actualTotalTime != null && it.value.plan.nodeType == "Seq Scan") {
                indexReport.addSequenceScan(it.value.plan.actualTotalTime, it.value)
            }
            if (it.value.plan.plans != null) {
                it.value.plan.plans.each { subPlan ->
                    if (it.value.plan.actualTotalTime != null && subPlan.nodeType == "Seq Scan") {
                        indexReport.addSequenceScan(subPlan.actualTotalTime, it.value)
                    }
                }
            }
        }

        return indexReport
    }

    void resolveColumnDataTypes(List<SourceCodeExtract> scannedFileList) {
        def map = new HashMap<String, SourceCodeExtract>()
        for (SourceCodeExtract visitor : scannedFileList) {
            //column joins
            def queryColumnJoin = visitor.queryColumnJoinExtractor
            if (!queryColumnJoin.columnJoinMap.isEmpty()) {
                map.put(queryColumnJoin.qualifiedClassName, visitor)
                queryColumnJoin.columnJoinMap.each {
                    def qualifiedJoinTableName = aliasMap.getQualifiedTableName(it.key)
                    def fullTableColumnName = aliasMap.getQualifiedTableName(queryColumnJoin.className) + "." + qualifiedJoinTableName
                    columnJoinMap.put(fullTableColumnName, it.value)
                }
            }

            //get data types
            def dataTypeExtractor = visitor.queryColumnDataTypeExtractor
            if (!dataTypeExtractor.columnDataTypeMap.isEmpty()) {
                map.put(dataTypeExtractor.qualifiedClassName, visitor)
                dataTypeExtractor.getColumnDataTypeMap().each {
                    def qualifiedColumnName = aliasMap.getQualifiedColumnName(dataTypeExtractor.className, it.key)
                    def fullTableColumnName = aliasMap.getQualifiedTableName(dataTypeExtractor.className) + "." + qualifiedColumnName
                    databaseDataType.addDataType(fullTableColumnName, it.value)
                }
            }
        }

        //extends thing
        for (SourceCodeExtract visitor : scannedFileList) {
            //column aliases
            def queryColumnDataType = visitor.queryColumnDataTypeExtractor
            if (!queryColumnDataType.columnDataTypeMap.isEmpty()) {
                def classExtendsPath = queryColumnDataType.extendsClassPath
                if (!classExtendsPath.isEmpty()) {
                    classExtendsPath.each {
                        ClassOrInterfaceDeclaration declaration = CodeLocator.locateClassOrInterfaceDeclaration(jNomad.typeSolver, it)
                        if (declaration != null) {
                            CompilationUnit unit = CodeLocator.demandCompilationUnit(declaration)
                            def qualifiedName = ""
                            if (unit.package.isPresent()) {
                                qualifiedName = unit.package.get().name.qualifiedName + "." + declaration.name
                            }

                            def value = map.get(qualifiedName)
                            if (value != null) {
                                value.queryColumnDataTypeExtractor.columnDataTypeMap.each {
                                    def qualifiedColumnName = aliasMap.getQualifiedColumnName(queryColumnDataType.className, it.key)
                                    def fullTableColumnName = aliasMap.getQualifiedTableName(queryColumnDataType.className) + "." + qualifiedColumnName
                                    databaseDataType.addDataType(fullTableColumnName, it.value)
                                }
                            }
                        }
                    }
                }
            }

        }

        //translate hibernate data types to join column data type
        databaseDataType.tableColumnDataTypeMap.each {
            if (!databaseDataType.isKnownDataType(it.value)) {
                def str = aliasMap.getQualifiedTableName(it.value) + "." + it.key.split(Pattern.quote("."))[1]
                def value = databaseDataType.tableColumnDataTypeMap.get(str)
                if (value != null) {
                    it.value = value
                }
            }
        }
    }

    private void explainQuery(Connection connection, String query, String originalQuery, SourceCodeExtract extract, Statement statement) {
        println "Explaining query: " + query

        connection.autoCommit = false
        def dbStatement = connection.prepareStatement("explain (analyze ${jNomad.queryExplainAnalyze}, format json) " + query)
        try {
            def result = dbStatement.executeQuery()
            while (result.next()) {
                ResultSetMetaData metaData = result.getMetaData()
                int columnCount = metaData.getColumnCount()

                for (int i = 1; i <= columnCount; i++) {
                    String name = metaData.getColumnName(i)
                    def str = result.getString(name)

                    ObjectMapper mapper = new ObjectMapper()
                    List<PostgresExplain> queryExplain = mapper.readValue(str,
                            new TypeReference<List<PostgresExplain>>() {})

                    postgresExplainMap.put(query, queryExplain.get(0))
                    successfullyExplainedQueryList.add(originalQuery)
                    extract.addQueryExplainResult(queryExplain.get(0))
                    queryExplain.get(0).originalQuery = originalQuery
                    queryExplain.get(0).finalQuery = query
                    queryExplain.get(0).explainedStatement = statement
                    queryExplain.get(0).sourceCodeExtract = extract
                }
            }
        } finally {
            connection.rollback()
            if (dbStatement != null) dbStatement.close()
        }
    }

    @Override
    PostgresDatabaseDataType getDatabaseDataType() {
        return databaseDataType
    }

    @Override
    String getSQLTableName(String tableName) {
        def name = aliasMap.getQualifiedTableName(tableName)
        if (name != null) {
            return name
        } else {
            return tableName
        }
    }

    @Override
    String getSQLColumnName(String tableName, String columnName) {
        return aliasMap.getQualifiedColumnName(tableName, columnName)
    }

    @Override
    String getSQLTableColumnName(String tableName, String columnName) {
        return getSQLTableName(tableName) + "." + aliasMap.getQualifiedColumnName(tableName, columnName)
    }

    @Override
    String getSQLJoinColumnName(String tableName, String joinTableName) {
        return columnJoinMap.get(getSQLTableName(tableName) + "." + getSQLTableName(joinTableName))
    }

    @Override
    String getFailedQueryReason(String originalQuery) {
        return failedQueryReasonMap.get(originalQuery)
    }

    @Override
    Map<String, SourceCodeExtract> getSourceCodeExtractMap() {
        return sourceCodeExtractMap
    }

}
