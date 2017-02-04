package com.codebrig.jnomad.task.parse.rank

import net.sf.jsqlparser.expression.Expression

import java.util.concurrent.ConcurrentHashMap

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class TableRank {

    private String tableName
    private Map<String, ColumnHitMap> tableColumnHitMap = new ConcurrentHashMap<>()
    private Map<String, String> tableColumnAliasMap = new ConcurrentHashMap<>()

    TableRank(String tableName) {
        this.tableName = tableName
    }

    String getTableName() {
        return tableName
    }

    void addColumnAlias(String columnAlias, String columnName) {
        tableColumnAliasMap.put(columnAlias.toLowerCase(), columnName.toLowerCase())
    }

    String getQualifiedColumnName(String columnName) {
        if (columnName == null) return null
        def columnAlias = tableColumnAliasMap.get(columnName.toLowerCase())
        if (columnAlias != null) {
            columnName = columnAlias
        }
        return columnName
    }

    void incrementColumnHitCount(String columnName, Expression expression) {
        columnName = getQualifiedColumnName(columnName)
        def hitMap = tableColumnHitMap.get(columnName)
        if (hitMap == null) {
            tableColumnHitMap.put(columnName, hitMap = new ColumnHitMap(this, columnName))
        }

        hitMap.hitCount.incrementAndGet()
        hitMap.expressionList.add(expression)
    }

    int getColumnHitCount(String columnName) {
        def hitMap = tableColumnHitMap.get(getQualifiedColumnName(columnName))
        if (hitMap == null) {
            return 0
        } else {
            return hitMap.hitCount.get()
        }
    }

    int getTotalColumnHitCount() {
        int totalHitCount = 0
        for (ColumnHitMap hitMap : tableColumnHitMap.values()) {
            totalHitCount += hitMap.hitCount.get()
        }
        return totalHitCount
    }

    Collection<ColumnHitMap> getAllColumnHitMaps() {
        return tableColumnHitMap.values()
    }

    boolean equals(o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false
        TableRank tableRank = (TableRank) o
        if (tableName != tableRank.tableName) return false
        return true
    }

    int hashCode() {
        return tableName.hashCode()
    }

}
