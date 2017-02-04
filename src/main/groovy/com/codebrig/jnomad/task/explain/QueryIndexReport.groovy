package com.codebrig.jnomad.task.explain

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.model.SourceCodeIndexReport

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
abstract class QueryIndexReport {
    
    protected final JNomad jNomad

    QueryIndexReport(JNomad jNomad) {
        this.jNomad = jNomad
    }

    JNomad getjNomad() {
        return jNomad
    }

    abstract DatabaseDataType getDatabaseDataType()

    abstract SourceCodeIndexReport createSourceCodeIndexReport()

    abstract String getSQLTableName(String tableName)

    abstract String getSQLColumnName(String tableName, String columnName)

    abstract String getSQLTableColumnName(String tableName, String columnName)

    abstract String getSQLJoinColumnName(String tableName, String joinTableName)

}
