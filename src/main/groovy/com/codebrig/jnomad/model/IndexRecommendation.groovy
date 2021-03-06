package com.codebrig.jnomad.model

import com.codebrig.jnomad.task.explain.DatabaseDataType
import com.codebrig.jnomad.task.explain.CalculatedExplainPlan
import com.codebrig.jnomad.utils.HQLQueryWalker
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
        private List<CalculatedExplainPlan> hitList = new ArrayList<>()

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

        void increaseHitCount(CalculatedExplainPlan plan) {
            hitList.add(Objects.requireNonNull(plan))
            hitCount.incrementAndGet()
        }

        String getTableName() {
            return tableName
        }

        Object getToIndex() {
            return toIndex
        }

        List<CalculatedExplainPlan> getHitList() {
            return hitList
        }
    }

    TreeMap<Integer, IndexHitMap> getIndexHitMapTreeMap() {
        def treeMap = new TreeMap<Integer, IndexHitMap>()
        indexMap.values().each {
            it.values().each {
                treeMap.put((int) (it.hitCount * it.priorityMultiplier), it)
            }
        }
        return treeMap
    }

    void determineBestIndex(CalculatedExplainPlan plan, DescriptiveStatistics stats) {
        def conditionStatementString = plan.explainPlan.conditionClause
        if (conditionStatementString == null) {
            //skip looking for index; mysql = null when index_condition is used instead of attached_condition
            return
        }

        def statementHitCache = statementIndexHitMap.get(plan.statement)
        if (statementHitCache != null) {
            statementHitCache.increaseHitCount(plan)

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
                    String tableName = Objects.requireNonNull(plan.explainPlan.tableName)
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
                    tableExpressionIndexMap.increaseHitCount(plan)
                    statementIndexHitMap.put(plan.statement, tableExpressionIndexMap)

                    if (plan.costScore > stats.mean + stats.standardDeviation) {
                        double multiplier = new Pow().value(((plan.costScore - stats.mean) / stats.standardDeviation), 2)
                        tableExpressionIndexMap.increasePriorityMultiplier(multiplier)
                    }
                } else {
                    //index field to the left
                    String tableName = Objects.requireNonNull(plan.explainPlan.tableName)
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
                    tableExpressionIndexMap.increaseHitCount(plan)
                    statementIndexHitMap.put(plan.statement, tableExpressionIndexMap)

                    if (plan.costScore > stats.mean + stats.standardDeviation) {
                        double multiplier = new Pow().value(((plan.costScore - stats.mean) / stats.standardDeviation), 2)
                        tableExpressionIndexMap.increasePriorityMultiplier(multiplier)
                    }
                }
            }
        }
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
