package com.codebrig.jnomad.task.explain

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.model.SourceCodeIndexReport

import java.sql.Connection

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
abstract class QueryIndexReport {
    
    private final JNomad jNomad
    private final List<String> allQueryList = new ArrayList<>()
    private final List<String> successfullyExplainedQueryList = new ArrayList<>()
    private final List<String> missingRequiredTableList = new ArrayList<>()
    private final List<String> missingRequiredColumnList = new ArrayList<>()
    private final List<String> permissionDeniedTableList = new ArrayList<>()
    private final List<String> failedQueryParseList = new ArrayList<>()

    QueryIndexReport(JNomad jNomad) {
        this.jNomad = Objects.requireNonNull(jNomad)
    }

    JNomad getjNomad() {
        return jNomad
    }

    List<String> getAllQueryList() {
        return allQueryList
    }

    List<String> getSuccessfullyExplainedQueryList() {
        return successfullyExplainedQueryList
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

    abstract DatabaseDataType getDatabaseDataType()

    abstract SourceCodeIndexReport createSourceCodeIndexReport(List<SourceCodeExtract> scannedFileList)

    abstract SourceCodeIndexReport createSourceCodeIndexReport(List<SourceCodeExtract> scannedFileList, Connection... dbConnections)

    abstract String getSQLTableName(String tableName)

    abstract String getSQLColumnName(String tableName, String columnName)

    abstract String getSQLTableColumnName(String tableName, String columnName)

    abstract String getSQLJoinColumnName(String tableName, String joinTableName)

    abstract String getFailedQueryReason(String originalQuery)

    abstract Map<String, SourceCodeExtract> getSourceCodeExtractMap()

}
