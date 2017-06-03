package com.codebrig.jnomad.task.parse

import com.google.common.collect.Maps

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
public class QueryEntityAliasMap {

    private final Map<String, String> tableNameAliasMap
    private final Map<String, String> columnNameAliasMap

    public QueryEntityAliasMap() {
        this.tableNameAliasMap = Maps.newConcurrentMap()
        this.columnNameAliasMap = Maps.newConcurrentMap()
    }

    public QueryEntityAliasMap(Map<String, String> tableNameAliasMap, Map<String, String> columnNameAliasMap) {
        this.tableNameAliasMap = tableNameAliasMap
        this.columnNameAliasMap = columnNameAliasMap
    }

    public void putAllTableNameAlias(Map<String, String> tableNameAliasMap) {
        this.tableNameAliasMap.putAll(tableNameAliasMap)
    }

    public void addTableNameAlias(String tableName, String alias) {
        tableNameAliasMap.put(Objects.requireNonNull(tableName), Objects.requireNonNull(alias))
    }

    public void putAllTableColumnNameAlias(Map<String, String> columnNameAliasMap) {
        this.columnNameAliasMap.putAll(columnNameAliasMap)
    }

    public void addTableColumnNameAlias(String tableColumnName, String alias) {
        columnNameAliasMap.put(Objects.requireNonNull(tableColumnName), Objects.requireNonNull(alias))
    }

    public String getTableName(String aliasTableName) {
        def tableName = tableNameAliasMap.get(aliasTableName)
        if (tableName == null) {
            return aliasTableName
        }
        return tableName
    }

    public String getColumnName(String aliasTableColumnName) {
        def columnName = columnNameAliasMap.get(aliasTableColumnName)
        if (columnName == null) {
            return aliasTableColumnName
        }
        return columnName
    }

    String getQualifiedColumnName(String tableName, String columnName) {
        def name = columnNameAliasMap.get(getQualifiedTableName(tableName) + "." + columnName)
        if (name != null) {
            return name
        }
        return columnName
    }

    String getQualifiedTableName(String tableName) {
        def name = tableNameAliasMap.get(tableName.toLowerCase())
        if (name != null) {
            return name
        }
        return tableName
    }

    Map<String, String> getTableNameAliasMap() {
        return new HashMap<>(tableNameAliasMap)
    }

    Map<String, String> getColumnNameAliasMap() {
        return new HashMap<>(columnNameAliasMap)
    }
}
