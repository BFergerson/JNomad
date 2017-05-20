package com.codebrig.jnomad.model

import com.codebrig.jnomad.task.explain.adapter.postgres.CalculatedExplainPlan
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresExplain
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class SourceCodeIndexReport {

    private TreeMap<Double, PostgresExplain> totalCostMap = new TreeMap<>()
    private TreeMap<Double, PostgresExplain> startupCostMap = new TreeMap<>()
    private TreeMap<Double, PostgresExplain> totalRuntimeMap = new TreeMap<>()
    private TreeMap<Double, PostgresExplain> executionTimeMap = new TreeMap<>()
    private TreeMap<Double, PostgresExplain> sequenceScanMap = new TreeMap<>()

    private IndexRecommendation indexRecommendation = new IndexRecommendation()

    IndexRecommendation getIndexRecommendation() {
        return indexRecommendation
    }

    void addTotalCost(Double key, PostgresExplain value) {
        this.totalCostMap.put(key, value)
    }

    void addStartupCost(Double key, PostgresExplain value) {
        this.startupCostMap.put(key, value)
    }

    void addTotalRuntime(Double key, PostgresExplain value) {
        this.totalRuntimeMap.put(key, value)
    }

    void addExecutionTime(Double key, PostgresExplain value) {
        this.executionTimeMap.put(key, value)
    }

    void addSequenceScan(Double key, PostgresExplain value) {
        this.sequenceScanMap.put(key, value)
    }

    TreeMap<Double, PostgresExplain> getTotalCostMap() {
        def map = new TreeMap<Double, PostgresExplain>()
        map.putAll(totalCostMap)
        return map
    }

    TreeMap<Double, PostgresExplain> getStartupCostMap() {
        def map = new TreeMap<Double, PostgresExplain>()
        map.putAll(startupCostMap)
        return map
    }

    TreeMap<Double, PostgresExplain> getTotalRuntimeMap() {
        def map = new TreeMap<Double, PostgresExplain>()
        map.putAll(totalRuntimeMap)
        return map
    }

    TreeMap<Double, PostgresExplain> getExecutionTimeMap() {
        def map = new TreeMap<Double, PostgresExplain>()
        map.putAll(executionTimeMap)
        return map
    }

    TreeMap<Double, PostgresExplain> getSequenceScanMap() {
        def map = new TreeMap<Double, PostgresExplain>()
        map.putAll(sequenceScanMap)
        return map
    }

    void addCalculatedExplainPlan(CalculatedExplainPlan calculatedExplainPlan, DescriptiveStatistics stats) {
        indexRecommendation.determineBestIndex(calculatedExplainPlan, stats)
    }

}
