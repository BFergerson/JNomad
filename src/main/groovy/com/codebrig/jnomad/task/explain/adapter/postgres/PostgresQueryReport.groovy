package com.codebrig.jnomad.task.explain.adapter.postgres

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.model.SourceCodeIndexReport
import com.codebrig.jnomad.task.parse.QueryParser
import com.codebrig.jnomad.task.explain.DatabaseDataType
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

    private QueryParser queryParser
    private DatabaseDataType databaseDataType = new PostgresDatabaseDataType()

    private Map columnJoinMap = new HashMap<>()
    private List<String> allQueryList = new ArrayList<>()
    private List<String> successfullyExplainedQueryList = new ArrayList<>()
    private List<String> emptyWhereClauseList = new ArrayList<>()
    private List<String> missingRequiredTableList = new ArrayList<>()
    private List<String> missingRequiredColumnList = new ArrayList<>()
    private List<String> permissionDeniedTableList = new ArrayList<>()
    private List<String> failedQueryParseList = new ArrayList<>()
    private Map<String, PostgresExplain> postgresExplainMap = new HashMap<>()

    PostgresQueryReport(JNomad jNomad, QueryParser queryParser) {
        super(jNomad)
        this.queryParser = queryParser
    }

    @Override
    SourceCodeIndexReport createSourceCodeIndexReport() {
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

        try {
            def map = new HashMap<String, SourceCodeExtract>()
            for (SourceCodeExtract visitor : jNomad.scannedFileList) {
                //column joins
                def queryColumnJoin = visitor.queryColumnJoinExtractor
                if (!queryColumnJoin.columnJoinMap.isEmpty()) {
                    map.put(queryColumnJoin.qualifiedClassName, visitor)
                    queryColumnJoin.columnJoinMap.each {
                        def qualifiedJoinTableName = queryParser.getQualifiedTableName(it.key)
                        def fullTableColumnName = queryParser.getQualifiedTableName(queryColumnJoin.className) + "." + qualifiedJoinTableName
                        columnJoinMap.put(fullTableColumnName, it.value)
                    }
                }

                //get data types
                def dataTypeExtractor = visitor.queryColumnDataTypeExtractor
                if (!dataTypeExtractor.columnDataTypeMap.isEmpty()) {
                    map.put(dataTypeExtractor.qualifiedClassName, visitor)
                    dataTypeExtractor.getColumnDataTypeMap().each {
                        def qualifiedColumnName = queryParser.getQualifiedColumnName(dataTypeExtractor.className, it.key)
                        def fullTableColumnName = queryParser.getQualifiedTableName(dataTypeExtractor.className) + "." + qualifiedColumnName
                        databaseDataType.addDataType(fullTableColumnName, it.value)
                    }
                }
            }

            //extends thing
            for (SourceCodeExtract visitor : jNomad.scannedFileList) {
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
                                        def qualifiedColumnName = queryParser.getQualifiedColumnName(queryColumnDataType.className, it.key)
                                        def fullTableColumnName = queryParser.getQualifiedTableName(queryColumnDataType.className) + "." + qualifiedColumnName
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
                    def str = queryParser.getQualifiedTableName(it.value) + "." + it.key.split(Pattern.quote("."))[1]
                    def value = databaseDataType.tableColumnDataTypeMap.get(str)
                    if (value != null) {
                        it.value = value
                    }
                }
            }

            for (SourceCodeExtract extract : jNomad.scannedFileList) {
                for (Statement statement : extract.parsedQueryList) {
                    def originalQuery = statement.toString()
                    allQueryList.add(originalQuery)

                    def query
                    try {
                        statement.accept(new HQLTableNameTransformer(this))
                        statement.accept(new HQLColumnNameTransformer(this))
                        statement.accept(new HQLTableJoinTransformer(this))
                        statement.accept(new HQLColumnNameTransformer(this)) //fix column names again after joins added
                        statement.accept(new HQLColumnValueTransformer(this))

                        query = QueryCleaner.cleanQueryExecute(statement.toString())
                        if (!query.toLowerCase().contains(" where ")) {
                            emptyWhereClauseList.add(query)
                            continue
                        }
                    } catch (HQLTransformException ex) {
                        println ex.getMessage()
                        println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                        failedQueryParseList.add(originalQuery)
                        continue
                    } catch (Exception e) {
                        e.printStackTrace()
                        println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                        failedQueryParseList.add(originalQuery)
                        continue
                    }

                    boolean explained = false
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

                                missingRequiredColumnList.add(originalQuery)
                                println "Missing required columns for query: " + query + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.serverErrorMessage.message
                                println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                            } else if (ex.serverErrorMessage.routine == "parserOpenTable") {
                                if ((i + 1) < connectionList.size()) {
                                    continue //try next connection
                                }

                                missingRequiredTableList.add(originalQuery)
                                println "Missing required tables for query: " + query + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.serverErrorMessage.message
                                println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                            } else if (ex.serverErrorMessage.routine == "aclcheck_error") {
                                permissionDeniedTableList.add(originalQuery)
                                println "Permission denied for query: " + query + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.serverErrorMessage.message
                                println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                            } else {
                                failedQueryParseList.add(originalQuery)
                                println "Failed to execute explain on query: " + statement.toString() + "\n\tOriginal query: " + originalQuery + "\n\tReason: " + ex.serverErrorMessage.message
                                println "\tSource code file: " + extract.queryLiteralExtractor.getClassName()
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace()
                            failedQueryParseList.add(originalQuery)
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

    private void explainQuery(Connection connection, String query, String originalQuery, SourceCodeExtract extract, Statement statement) {
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
    DatabaseDataType getDatabaseDataType() {
        return databaseDataType
    }

    @Override
    String getSQLTableName(String tableName) {
        def name = queryParser.getQualifiedTableName(tableName)
        if (name != null) {
            return name
        } else {
            return tableName
        }
    }

    @Override
    String getSQLColumnName(String tableName, String columnName) {
        return queryParser.getQualifiedColumnName(tableName, columnName)
    }

    @Override
    String getSQLTableColumnName(String tableName, String columnName) {
        return getSQLTableName(tableName) + "." + queryParser.getQualifiedColumnName(tableName, columnName)
    }

    @Override
    String getSQLJoinColumnName(String tableName, String joinTableName) {
        return columnJoinMap.get(getSQLTableName(tableName) + "." + getSQLTableName(joinTableName))
    }

    List<String> getAllQueryList() {
        return allQueryList
    }

    List<String> getEmptyWhereClauseList() {
        return emptyWhereClauseList
    }

    List<String> getMissingRequiredTableList() {
        return missingRequiredTableList
    }

    List<String> getMissingRequiredColumnList() {
        return missingRequiredColumnList
    }

    List<String> getPermissionDeniedTableList() {
        return permissionDeniedTableList
    }

    List<String> getFailedQueryParseList() {
        return failedQueryParseList
    }

    List<String> getSuccessfullyExplainedQueryList() {
        return successfullyExplainedQueryList
    }

}
