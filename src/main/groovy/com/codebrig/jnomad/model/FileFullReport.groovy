package com.codebrig.jnomad.model;

import com.codebrig.jnomad.JNomad;
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresExplain;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.Precision

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
public class FileFullReport {

    private final File file
    private final List<QueryScore> queryScoreList
    private final List<RecommendedIndex> recommendedIndexList

    public FileFullReport(File file, JNomad jNomad, SourceCodeIndexReport indexReport) {
        this.file = file
        queryScoreList = new ArrayList<>()
        recommendedIndexList = new ArrayList<>()

        reportQueriesTask(jNomad, indexReport)
    }

    private void reportQueriesTask(JNomad jNomad, SourceCodeIndexReport indexReport) {
        def totalRuntimeStats = new DescriptiveStatistics()
        def executionTimeStats = new DescriptiveStatistics()
        def totalCostStats = new DescriptiveStatistics()
        def sequenceScanStats = new DescriptiveStatistics()
        calculateQueryScores(indexReport.totalRuntimeMap, totalRuntimeStats)
        calculateQueryScores(indexReport.executionTimeMap, executionTimeStats)
        calculateQueryScores(indexReport.totalCostMap, totalCostStats)
        calculateQueryScores(indexReport.sequenceScanMap, sequenceScanStats)

        //finally, recommend some indexes
        if (!indexReport.totalRuntimeMap.isEmpty()) {
            calculatedExplainPlan(indexReport, indexReport.totalRuntimeMap, totalRuntimeStats)
        }
        if (!indexReport.executionTimeMap.isEmpty()) {
            calculatedExplainPlan(indexReport, indexReport.executionTimeMap, executionTimeStats)
        }
        if (!indexReport.totalCostMap.isEmpty()) {
            calculatedExplainPlan(indexReport, indexReport.totalCostMap, totalCostStats)
        }
        if (!indexReport.sequenceScanMap.isEmpty()) {
            calculatedExplainPlan(indexReport, indexReport.sequenceScanMap, sequenceScanStats)
        }

        int recommendationCount = 0
        def map = indexReport.indexRecommendation.indexHitMapTreeMap.descendingMap()
        map.each {
            if (it.key >= jNomad.indexPriorityThreshold) {
                RecommendedIndex rIndex = new RecommendedIndex()
                rIndex.indexCreateSQL = "CREATE INDEX ON ${it.value.tableName} (${it.value.toIndex.toString()});"
                rIndex.indexPriority = Precision.round(it.key, 0)
                rIndex.indexTable = it.value.tableName
                rIndex.indexCondition = it.value.toIndex.toString()
                recommendedIndexList.add(rIndex)

                //unique query locations this index would likely improve
                def locationSet = new HashSet<>()
                it.value.hitList.each {
                    def literalExtractor = it.postgresExplain.sourceCodeExtract.queryLiteralExtractor
                    def range = literalExtractor.getQueryCallRange(it.postgresExplain.originalQuery)
                    if (!locationSet.contains(range)) {
                        rIndex.addToIndexAffectMap(literalExtractor.sourceFile, range)
                    }
                    locationSet.add(range)
                }
                recommendationCount++
            }
        }
    }

    private static
    void calculatedExplainPlan(SourceCodeIndexReport indexReport, TreeMap<Double, PostgresExplain> reportMap, DescriptiveStatistics stats) {
        def top = reportMap.pollLastEntry()
        while (top != null) {
            def val = top.value.calculateCostliestNode(Arrays.asList("Nested Loop", "Aggregate"))
            val.costScore = top.key
            indexReport.addCalculatedExplainPlan(val, stats)

            val = top.value.calculateSlowestNode(Arrays.asList("Nested Loop", "Aggregate"))
            val.costScore = top.key
            indexReport.addCalculatedExplainPlan(val, stats)

            top = reportMap.pollLastEntry()
        }
    }

    private void calculateQueryScores(TreeMap<Double, PostgresExplain> reportMap, DescriptiveStatistics stats) {
        def top = reportMap.pollLastEntry()
        while (top != null) {
            stats.addValue(top.key)

            QueryScore queryScore = new QueryScore()
            queryScore.score = top.key
            queryScore.originalQuery = top.value.originalQuery
            queryScore.explainedQuery = top.value.finalQuery
            queryScore.sourceCodeExtract = top.value.sourceCodeExtract
            queryScore.explain = top.value
            queryScore.queryFile = file

            def literalExtractor = top.value.sourceCodeExtract.queryLiteralExtractor
            def range = literalExtractor.getQueryCallRange(top.value.originalQuery)
            queryScore.queryLocation = range

            queryScoreList.add(queryScore)
            top = reportMap.pollLastEntry()
        }
    }

    public File getFile() {
        return file
    }

    public IndexRecommendation getIndexRecommendation() {
        return indexRecommendation
    }

    List<RecommendedIndex> getRecommendedIndexList() {
        return recommendedIndexList
    }

    List<QueryScore> getQueryScoreList() {
        return queryScoreList
    }

}
