package com.codebrig.jnomad.task.explain.adapter.mysql

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.model.SourceCodeIndexReport
import com.codebrig.jnomad.task.explain.QueryIndexReport
import com.codebrig.jnomad.task.explain.adapter.postgres.MysqlDatabaseDataType
import com.codebrig.jnomad.task.explain.transform.hql.HQLColumnNameTransformer
import com.codebrig.jnomad.task.explain.transform.hql.HQLColumnValueTransformer
import com.codebrig.jnomad.task.explain.transform.hql.HQLTableJoinTransformer
import com.codebrig.jnomad.task.explain.transform.hql.HQLTableNameTransformer
import com.codebrig.jnomad.task.explain.transform.hql.HQLTransformException
import com.codebrig.jnomad.task.parse.QueryEntityAliasMap
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
class MysqlQueryReport extends QueryIndexReport  {

    private QueryEntityAliasMap aliasMap
    private MysqlDatabaseDataType databaseDataType

    private Map columnJoinMap = new HashMap<>()

    private Map<String, SourceCodeExtract> sourceCodeExtractMap = new HashMap<>()
    private Map<String, MysqlExplain> mysqlExplainMap = new HashMap<>()
    private Map<String, String> failedQueryReasonMap = new HashMap<>()

    MysqlQueryReport(JNomad jNomad, MysqlDatabaseDataType databaseDataType, QueryEntityAliasMap aliasMap) {
        super(jNomad)
        this.databaseDataType = Objects.requireNonNull(databaseDataType)
        this.aliasMap = Objects.requireNonNull(aliasMap)

        if (jNomad.dbDatabase.isEmpty() || jNomad.dbHost.isEmpty()
                || jNomad.dbUsername.isEmpty() || jNomad.dbPassword.isEmpty()) {
            throw new RuntimeException("Mysql database access was not provided!")
        }
    }

    @Override
    SourceCodeIndexReport createSourceCodeIndexReport(List<SourceCodeExtract> scannedFileList) {
        List<Connection> connectionList = new ArrayList<>()
        try {
            for (int i = 0; i < jNomad.dbDatabase.size(); i++) {
                def host = jNomad.dbHost.get(i)
                if (!host.contains(":")) {
                    host += ":3306"
                }
                def connUrl = "jdbc:mysql://" + host + "/" + jNomad.dbDatabase.get(i)
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
                        if (!query.toLowerCase().contains(" where ")) {
                            emptyWhereClauseList.add(query)
                            continue
                        }
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
        mysqlExplainMap.each {
            if (it.value.queryBlock.rTotalTimeMs != null) {
                indexReport.addExecutionTime(it.value.queryBlock.rTotalTimeMs, it.value)
            }
        }

        return indexReport
    }

    private void explainQuery(Connection connection, String query, String originalQuery, SourceCodeExtract extract, Statement statement) {
        println "Explaining query: " + query

        connection.autoCommit = false
        def dbStatement = connection.prepareStatement("analyze format=json " + query)
        try {
            def result = dbStatement.executeQuery()
            while (result.next()) {
                ResultSetMetaData metaData = result.getMetaData()
                int columnCount = metaData.getColumnCount()

                for (int i = 1; i <= columnCount; i++) {
                    String name = metaData.getColumnName(i)
                    def str = result.getString(name)

                    ObjectMapper mapper = new ObjectMapper()
                    MysqlExplain queryExplain = mapper.readValue(str, new TypeReference<MysqlExplain>() {})

                    mysqlExplainMap.put(query, queryExplain)
                    successfullyExplainedQueryList.add(originalQuery)
                    extract.addQueryExplainResult(queryExplain)
                    queryExplain.originalQuery = originalQuery
                    queryExplain.finalQuery = query
                    queryExplain.explainedStatement = statement
                    queryExplain.sourceCodeExtract = extract
                }
            }
        } finally {
            connection.rollback()
            if (dbStatement != null) dbStatement.close()
        }
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

    @Override
    MysqlDatabaseDataType getDatabaseDataType() {
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
