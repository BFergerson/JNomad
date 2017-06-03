package com.codebrig.jnomad.model

import com.codebrig.jnomad.task.explain.adapter.postgres.CalculatedExplainPlan
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresExplain
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class SourceCodeIndexReport {

    private TreeMap<Double, List<PostgresExplain>> totalCostMap = new TreeMap<>()
    private TreeMap<Double, List<PostgresExplain>> startupCostMap = new TreeMap<>()
    private TreeMap<Double, List<PostgresExplain>> totalRuntimeMap = new TreeMap<>()
    private TreeMap<Double, List<PostgresExplain>> executionTimeMap = new TreeMap<>()
    private TreeMap<Double, List<PostgresExplain>> sequenceScanMap = new TreeMap<>()

    private IndexRecommendation indexRecommendation = new IndexRecommendation()

    IndexRecommendation getIndexRecommendation() {
        return indexRecommendation
    }

    void addTotalCost(Double key, PostgresExplain value) {
        List<PostgresExplain> explainList = totalCostMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            totalCostMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    void addStartupCost(Double key, PostgresExplain value) {
        List<PostgresExplain> explainList = startupCostMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            startupCostMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    void addTotalRuntime(Double key, PostgresExplain value) {
        List<PostgresExplain> explainList = totalRuntimeMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            totalRuntimeMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    void addExecutionTime(Double key, PostgresExplain value) {
        List<PostgresExplain> explainList = executionTimeMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            executionTimeMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    void addSequenceScan(Double key, PostgresExplain value) {
        List<PostgresExplain> explainList = sequenceScanMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            sequenceScanMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    TreeMap<Double, List<PostgresExplain>> getTotalCostMap() {
        def map = new TreeMap<Double, List<PostgresExplain>>()
        map.putAll(totalCostMap)
        return map
    }

    TreeMap<Double, List<PostgresExplain>> getStartupCostMap() {
        def map = new TreeMap<Double, List<PostgresExplain>>()
        map.putAll(startupCostMap)
        return map
    }

    TreeMap<Double, List<PostgresExplain>> getTotalRuntimeMap() {
        def map = new TreeMap<Double, List<PostgresExplain>>()
        map.putAll(totalRuntimeMap)
        return map
    }

    TreeMap<Double, List<PostgresExplain>> getExecutionTimeMap() {
        def map = new TreeMap<Double, List<PostgresExplain>>()
        map.putAll(executionTimeMap)
        return map
    }

    TreeMap<Double, List<PostgresExplain>> getSequenceScanMap() {
        def map = new TreeMap<Double, List<PostgresExplain>>()
        map.putAll(sequenceScanMap)
        return map
    }

    void addCalculatedExplainPlan(CalculatedExplainPlan calculatedExplainPlan, DescriptiveStatistics stats) {
        indexRecommendation.determineBestIndex(Objects.requireNonNull(calculatedExplainPlan), Objects.requireNonNull(stats))
    }

}
