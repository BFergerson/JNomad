package com.codebrig.jnomad.model

import com.codebrig.jnomad.task.explain.DatabaseDataType
import com.codebrig.jnomad.task.explain.adapter.postgres.CalculatedExplainPlan
import com.codebrig.jnomad.utils.HQLQueryWalker
import com.codebrig.jnomad.utils.HQLTableAliasWalker
import net.sf.jsqlparser.expression.BinaryExpression
import net.sf.jsqlparser.schema.Column
import net.sf.jsqlparser.statement.Statement
import org.apache.commons.math3.analysis.function.Pow
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class IndexRecommendation {

    private Map<String, Map<String, IndexHitMap>> indexMap = new HashMap<>()
    private Map<Statement, IndexHitMap> statementIndexHitMap = new HashMap<>()

    static class IndexHitMap {
        private AtomicInteger hitCount = new AtomicInteger(0)
        private double priorityMultiplier = 1.0
        private String tableName
        private Object toIndex

        IndexHitMap(String tableName, Object toIndex) {
            this.tableName = Objects.requireNonNull(tableName)
            this.toIndex = Objects.requireNonNull(toIndex)
        }

        void increasePriorityMultiplier(double increaseAmount) {
            priorityMultiplier += increaseAmount
        }

        double getPriorityMultiplier() {
            return priorityMultiplier
        }

        int getHitCount() {
            return hitCount.get()
        }

        void increaseHitCount() {
            hitCount.incrementAndGet()
        }

        String getTableName() {
            return tableName
        }

        Object getToIndex() {
            return toIndex
        }

    }

    TreeMap<Integer, IndexHitMap> getIndexHitMapTreeMap() {
        def treeMap = new TreeMap<Integer, IndexHitMap>()
        indexMap.values().each {
            it.values().each {
                treeMap.put(it.hitCount * it.priorityMultiplier, it)
            }
        }
        return treeMap
    }

    void determineBestIndex(CalculatedExplainPlan plan, DescriptiveStatistics stats) {
        def conditionStatementString = plan.plan.filter
        if (conditionStatementString == null) {
            conditionStatementString = plan.plan.recheckCond
        }

        def statementHitCache = statementIndexHitMap.get(plan.statement)
        if (statementHitCache != null) {
            statementHitCache.increaseHitCount()

            if (plan.costScore > stats.mean + stats.standardDeviation) {
                double multiplier = new Pow().value(((plan.costScore - stats.mean) / stats.standardDeviation), 2)
                statementHitCache.increasePriorityMultiplier(multiplier)
            }
        } else if (conditionStatementString != null) {
            Pattern pattern = Pattern.compile("'jnomad-(\\d+)'")
            Matcher matcher = pattern.matcher(conditionStatementString)
            while (matcher.find()) {
                def nomadId = matcher.group(1)
                findPossibleIndex(plan, nomadId, stats)
            }

            DatabaseDataType.JNOMAD_REGISTERED_IDS.each {
                pattern = Pattern.compile("(${it})")
                matcher = pattern.matcher(conditionStatementString)

                while (matcher.find()) {
                    def nomadId = matcher.group(1)
                    findPossibleIndex(plan, nomadId, stats)
                }
            }

            if (conditionStatementString.contains("1992-09-28")) {
                findPossibleIndex(plan, "{d '1992-09-28'}", stats)
            }
        }
    }

    void findPossibleIndex(CalculatedExplainPlan plan, String nomadId, DescriptiveStatistics stats) {
        plan.determineNomadParent(nomadId)
        if (plan.parentToNomadValue != null) {
            if (plan.parentToNomadValue instanceof BinaryExpression) {
                BinaryExpression compareOp = (BinaryExpression) plan.parentToNomadValue
                if (compareOp.leftExpression == plan.nomadValue) {
                    //index field to the right
                    def tableName = plan.plan.relationName //getTableName(plan.statement, compareOp.leftExpression)
                    removeTableNames(compareOp.rightExpression)
                    def toIndex = compareOp.rightExpression

                    def tableIndexMap = indexMap.get(tableName)
                    if (tableIndexMap == null) {
                        indexMap.put(tableName, tableIndexMap = new HashMap<String, IndexHitMap>())
                    }
                    def tableExpressionIndexMap = tableIndexMap.get(toIndex.toString())
                    if (tableExpressionIndexMap == null) {
                        tableIndexMap.put(toIndex.toString(), tableExpressionIndexMap = new IndexHitMap(tableName, toIndex))
                    }
                    tableExpressionIndexMap.increaseHitCount()
                    statementIndexHitMap.put(plan.statement, tableExpressionIndexMap)

                    if (plan.costScore > stats.mean + stats.standardDeviation) {
                        double multiplier = new Pow().value(((plan.costScore - stats.mean) / stats.standardDeviation), 2)
                        tableExpressionIndexMap.increasePriorityMultiplier(multiplier)
                    }
                } else {
                    //index field to the left
                    def tableName = plan.plan.relationName //getTableName(plan.statement, compareOp.leftExpression)
                    removeTableNames(compareOp.leftExpression)
                    def toIndex = compareOp.leftExpression

                    def tableIndexMap = indexMap.get(tableName)
                    if (tableIndexMap == null) {
                        indexMap.put(tableName, tableIndexMap = new HashMap<String, IndexHitMap>())
                    }
                    def tableExpressionIndexMap = tableIndexMap.get(toIndex.toString())
                    if (tableExpressionIndexMap == null) {
                        tableIndexMap.put(toIndex.toString(), tableExpressionIndexMap = new IndexHitMap(tableName, toIndex))
                    }
                    tableExpressionIndexMap.increaseHitCount()
                    statementIndexHitMap.put(plan.statement, tableExpressionIndexMap)

                    if (plan.costScore > stats.mean + stats.standardDeviation) {
                        double multiplier = new Pow().value(((plan.costScore - stats.mean) / stats.standardDeviation), 2)
                        tableExpressionIndexMap.increasePriorityMultiplier(multiplier)
                    }
                }
            }
        }
    }

    static String getTableName(Statement statement, Object obj) {
        String tableName = null
        def walker = new HQLTableAliasWalker()
        walker.visit(obj)
        walker.statementParsePath.each {
            if (it instanceof Column) {
                if (it.table != null) {
                    tableName = it.table.name
                }
            }
        }

        if (tableName != null) {
            def aliasWalker = new HQLTableAliasWalker()
            aliasWalker.visit(statement)

            def fullTableName = aliasWalker.queryTableAliasMap.get(tableName)
            if (fullTableName != null) {
                tableName = fullTableName
            }
        }
        return tableName
    }

    static void removeTableNames(Object obj) {
        def walker = new HQLQueryWalker()
        walker.visit(obj)
        walker.statementParsePath.each {
            if (it instanceof Column) {
                it.table = null
            }
        }
    }

}
