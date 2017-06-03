package com.codebrig.jnomad.model

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.task.explain.DatabaseDataType
import com.codebrig.jnomad.task.explain.QueryIndexReport
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresDatabaseDataType
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresExplain
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresQueryReport
import com.codebrig.jnomad.task.parse.QueryEntityAliasMap
import com.codebrig.jnomad.task.parse.QueryParser
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.util.Precision

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
public class FileFullReport {

    private final File file
    private final List<QueryScore> queryScoreList
    private final List<RecommendedIndex> recommendedIndexList
    private final QueryIndexReport queryIndexReport

    public FileFullReport(File file, JNomad jNomad) {
        this.file = file
        queryScoreList = new ArrayList<>()
        recommendedIndexList = new ArrayList<>()

        QueryParser queryParser = new QueryParser(jNomad)
        queryParser.run()
        queryIndexReport = new PostgresQueryReport(jNomad, new PostgresDatabaseDataType(), queryParser.aliasMap)
        SourceCodeIndexReport report = queryIndexReport.createSourceCodeIndexReport(jNomad.scannedFileList)
        reportQueriesTask(jNomad, report)
    }

    public FileFullReport(File file, JNomad jNomad, PostgresDatabaseDataType databaseDataType, QueryEntityAliasMap aliasMap, List<SourceCodeExtract> scannedFileList) {
        this.file = file
        queryScoreList = new ArrayList<>()
        recommendedIndexList = new ArrayList<>()
        queryIndexReport = new PostgresQueryReport(jNomad, databaseDataType, aliasMap)
        queryIndexReport.resolveColumnDataTypes(jNomad.scannedFileList)
        SourceCodeIndexReport report = queryIndexReport.createSourceCodeIndexReport(scannedFileList)
        reportQueriesTask(jNomad, report)
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
                        rIndex.addToIndexAffectMap(literalExtractor.sourceFile, range, it.postgresExplain.originalQuery)
                    }
                    locationSet.add(range)
                }
                recommendationCount++
            }
        }
    }

    private static
    void calculatedExplainPlan(SourceCodeIndexReport indexReport, TreeMap<Double, List<PostgresExplain>> reportMap, DescriptiveStatistics stats) {
        def top = reportMap.pollLastEntry()
        while (top != null) {
            for (PostgresExplain explain : top.value) {
                def val = explain.calculateCostliestNode(Arrays.asList("Nested Loop", "Aggregate"))
                val.costScore = top.key
                indexReport.addCalculatedExplainPlan(val, stats)

                val = explain.calculateSlowestNode(Arrays.asList("Nested Loop", "Aggregate"))
                val.costScore = top.key
                indexReport.addCalculatedExplainPlan(val, stats)
            }

            top = reportMap.pollLastEntry()
        }
    }

    private void calculateQueryScores(TreeMap<Double, List<PostgresExplain>> reportMap, DescriptiveStatistics stats) {
        def top = reportMap.pollLastEntry()
        while (top != null) {
            stats.addValue(top.key)

            for (PostgresExplain explain : top.value) {
                QueryScore queryScore = new QueryScore()
                queryScore.score = top.key
                queryScore.originalQuery = explain.originalQuery
                queryScore.explainedQuery = explain.finalQuery
                queryScore.sourceCodeExtract = explain.sourceCodeExtract
                queryScore.explain = explain
                queryScore.queryFile = file

                def literalExtractor = explain.sourceCodeExtract.queryLiteralExtractor
                def range = literalExtractor.getQueryCallRange(explain.originalQuery)
                queryScore.queryLocation = range

                queryScoreList.add(queryScore)
            }

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

    QueryIndexReport getQueryIndexReport() {
        return queryIndexReport
    }

    List<QueryScore> getQueryScoreList() {
        return queryScoreList
    }

}
