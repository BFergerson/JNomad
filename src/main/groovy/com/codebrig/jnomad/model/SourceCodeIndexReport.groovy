package com.codebrig.jnomad.model

import com.codebrig.jnomad.task.explain.ExplainResult
import com.codebrig.jnomad.task.explain.CalculatedExplainPlan
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresExplain
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class SourceCodeIndexReport {

    private TreeMap<Double, List<ExplainResult>> totalCostMap = new TreeMap<>()
    private TreeMap<Double, List<ExplainResult>> startupCostMap = new TreeMap<>()
    private TreeMap<Double, List<ExplainResult>> totalRuntimeMap = new TreeMap<>()
    private TreeMap<Double, List<ExplainResult>> executionTimeMap = new TreeMap<>()
    private TreeMap<Double, List<ExplainResult>> sequenceScanMap = new TreeMap<>()

    private IndexRecommendation indexRecommendation = new IndexRecommendation()

    IndexRecommendation getIndexRecommendation() {
        return indexRecommendation
    }

    void addTotalCost(Double key, ExplainResult value) {
        List<ExplainResult> explainList = totalCostMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            totalCostMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    void addStartupCost(Double key, ExplainResult value) {
        List<ExplainResult> explainList = startupCostMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            startupCostMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    void addTotalRuntime(Double key, ExplainResult value) {
        List<ExplainResult> explainList = totalRuntimeMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            totalRuntimeMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    void addExecutionTime(Double key, ExplainResult value) {
        List<ExplainResult> explainList = executionTimeMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            executionTimeMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    void addSequenceScan(Double key, PostgresExplain value) {
        List<ExplainResult> explainList = sequenceScanMap.get(key)
        if (explainList == null) {
            explainList = new ArrayList<>()
            sequenceScanMap.put(Objects.requireNonNull(key), explainList)
        }
        explainList.add(Objects.requireNonNull(value))
    }

    TreeMap<Double, List<ExplainResult>> getTotalCostMap() {
        def map = new TreeMap<Double, List<ExplainResult>>()
        map.putAll(totalCostMap)
        return map
    }

    TreeMap<Double, List<ExplainResult>> getStartupCostMap() {
        def map = new TreeMap<Double, List<ExplainResult>>()
        map.putAll(startupCostMap)
        return map
    }

    TreeMap<Double, List<ExplainResult>> getTotalRuntimeMap() {
        def map = new TreeMap<Double, List<ExplainResult>>()
        map.putAll(totalRuntimeMap)
        return map
    }

    TreeMap<Double, List<ExplainResult>> getExecutionTimeMap() {
        def map = new TreeMap<Double, List<ExplainResult>>()
        map.putAll(executionTimeMap)
        return map
    }

    TreeMap<Double, List<ExplainResult>> getSequenceScanMap() {
        def map = new TreeMap<Double, List<ExplainResult>>()
        map.putAll(sequenceScanMap)
        return map
    }

    void addCalculatedExplainPlan(CalculatedExplainPlan calculatedExplainPlan, DescriptiveStatistics stats) {
        indexRecommendation.determineBestIndex(Objects.requireNonNull(calculatedExplainPlan), Objects.requireNonNull(stats))
    }

}
