package com.codebrig.jnomad.task.parse.rank

import net.sf.jsqlparser.expression.Expression

import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class ColumnHitMap implements Comparable<ColumnHitMap> {

    public final TableRank parentTableRank
    public final String columnName
    public final AtomicInteger hitCount = new AtomicInteger(0)
    public final List<Expression> expressionList = new ArrayList<>()

    ColumnHitMap(TableRank parentTableRank, String columnName) {
        this.parentTableRank = parentTableRank
        this.columnName = columnName
    }

    @Override
    int compareTo(ColumnHitMap o) {
        this.hitCount.get() <=> o.hitCount.get()
    }

}