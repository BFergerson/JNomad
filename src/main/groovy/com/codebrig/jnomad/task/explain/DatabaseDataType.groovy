package com.codebrig.jnomad.task.explain

import net.sf.jsqlparser.expression.Expression
import net.sf.jsqlparser.statement.create.table.ColDataType

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
abstract class DatabaseDataType {

    public static final Set<String> JNOMAD_REGISTERED_IDS = new HashSet<String>()

    abstract void addDataType(String tableColumnName, String dataType)

    abstract Map<String, String> getTableColumnDataTypeMap()

    abstract boolean isKnownDataType(String dataType)

    abstract String generateNomadSQLValueAsText(String tableColumnName)

    abstract Expression generateNomadSQLValue(String tableColumnName)

    abstract String getDefaultSQLStringExpressionAsText()

    abstract Expression getDefaultSQLStringExpression()

    abstract String generateNomadSQLValueAsText(ColDataType dataType)

    abstract Expression generateNomadSQLValue(ColDataType dataType)

}
