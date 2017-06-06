package com.codebrig.jnomad.task.explain.adapter.postgres

import com.codebrig.jnomad.task.explain.DatabaseDataType
import net.sf.jsqlparser.expression.*
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.statement.create.table.ColDataType
import org.apache.commons.math3.random.RandomDataGenerator

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class MysqlDatabaseDataType extends DatabaseDataType {

    private Map<String, String> tableColumnDataTypeMap

    MysqlDatabaseDataType() {
        this.tableColumnDataTypeMap = new HashMap<>()
    }

    MysqlDatabaseDataType(Map<String, String> tableColumnDataTypeMap) {
        this.tableColumnDataTypeMap = tableColumnDataTypeMap
    }

    @Override
    void addDataType(String tableColumnName, String dataType) {
        tableColumnDataTypeMap.put(tableColumnName, dataType)
    }

    @Override
    Map<String, String> getTableColumnDataTypeMap() {
        return tableColumnDataTypeMap
    }

    @Override
    boolean isKnownDataType(String dataType) {
        switch (dataType) {
            case "Boolean":
            case "Long":
            case "Integer":
            case "Date":
            case "String":
                return true
            default: return false
        }
    }

    @Override
    String generateNomadSQLValueAsText(String tableColumnName) {
        def dataType = tableColumnDataTypeMap.get(tableColumnName)
        if (dataType == null) {
            return getDefaultSQLStringExpressionAsText()
        }

        switch (dataType) {
            case "Boolean":
                return "true"
            case "Integer":
                long id = new RandomDataGenerator().nextInt(0, Integer.MAX_VALUE)
                JNOMAD_REGISTERED_IDS.add(Long.toString(id))
                return id
            case "Long":
                long id = new RandomDataGenerator().nextLong(Integer.MAX_VALUE + 1, Long.MAX_VALUE)
                JNOMAD_REGISTERED_IDS.add(Long.toString(id))
                return id
            case "Date":
                return "'1992-09-28'"
            case "String":
            default: return getDefaultSQLStringExpressionAsText()
        }
    }

    @Override
    Expression generateNomadSQLValue(String tableColumnName) {
        String textValue = generateNomadSQLValueAsText(tableColumnName)
        def dataType = tableColumnDataTypeMap.get(tableColumnName)
        if (dataType == null) {
            return getDefaultSQLStringExpression()
        }

        switch (dataType) {
            case "Boolean":
                return new Column(textValue)
            case "Integer":
            case "Long":
                return new LongValue(textValue)
            case "Date":
                return new DateValue(textValue)
            case "String":
            default: return getDefaultSQLStringExpression()
        }
    }

    @Override
    String getDefaultSQLStringExpressionAsText() {
        return "'jnomad-${System.nanoTime()}'" //default to string
    }

    @Override
    Expression getDefaultSQLStringExpression() {
        return new StringValue(getDefaultSQLStringExpressionAsText()) //default to string
    }

    @Override
    String generateNomadSQLValueAsText(ColDataType dataType) {
        switch (dataType.dataType.toLowerCase()) {
            case "integer":
                long id = new RandomDataGenerator().nextInt(0, Integer.MAX_VALUE)
                JNOMAD_REGISTERED_IDS.add(Long.toString(id))
                return id
            case "long":
                long id = new RandomDataGenerator().nextLong(Integer.MAX_VALUE + 1, Long.MAX_VALUE)
                JNOMAD_REGISTERED_IDS.add(Long.toString(id))
                return id
            case "timestamp":
                return "'1992-09-28 10:10:15'"
            case "date":
                return "'1992-09-28'"
            default: return getDefaultSQLStringExpressionAsText() //default to string
        }
    }

    @Override
    Expression generateNomadSQLValue(ColDataType dataType) {
        String textValue = generateNomadSQLValueAsText(dataType)
        switch (dataType.dataType.toLowerCase()) {
            case "integer":
            case "long":
                return new LongValue(textValue)
            case "timestamp":
                return new TimestampValue(textValue)
            case "date":
                return new DateValue(textValue)
            default: new StringValue(textValue) //default to string
        }
    }

}
