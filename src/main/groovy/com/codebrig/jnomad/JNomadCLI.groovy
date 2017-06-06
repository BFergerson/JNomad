package com.codebrig.jnomad

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.ParameterException
import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.model.SourceCodeIndexReport
import com.codebrig.jnomad.task.explain.QueryIndexReport
import com.codebrig.jnomad.task.explain.adapter.DatabaseAdapterType
import com.codebrig.jnomad.task.explain.adapter.mysql.MysqlQueryReport
import com.codebrig.jnomad.task.explain.adapter.postgres.MysqlDatabaseDataType
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresDatabaseDataType
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresExplain
import com.codebrig.jnomad.task.parse.QueryEntityAliasMap
import com.codebrig.jnomad.task.parse.QueryParser
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresQueryReport
import com.codebrig.jnomad.utils.CodeLocator
import com.google.common.base.MoreObjects
import com.google.common.collect.ImmutableList
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.apache.commons.math3.util.Precision

import java.util.concurrent.TimeUnit

/**
 * Main entry; Command-line parser;
 *
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class JNomadCLI {

    public static final String JNOMAD_VERSION = "1.5/Alpha"
    public static final String JNOMAD_BUILD_DATE = "2017.06.06"

    @Parameter(names = ["-f", "-log_file"], description = "Log console output to specified file")
    public String logFile

    @Parameter(names = "-source_directory", description = "Directory/directories of Java source code to be used for type solving")
    public List<String> sourceDirectoryList

    @Parameter(names = "-scan_file", description = "File(s) of Java source code to be scanned for queries")
    public List<String> scanFileList

    @Parameter(names = "-scan_directory", description = "Directory/directories of Java source code to be scanned for queries")
    public List<String> scanDirectoryList

    @Parameter(names = "-scan_recursive", description = "Scan source directory/directories recursively")
    public boolean recursiveDirectoryScan = true

    @Parameter(names = "-source_directory_prescan", description = "Pre-scan scan directory for available source directories", arity = 1)
    public boolean sourceDirectoryPrescan = true

    @Parameter(names = "-scan_thread_count", description = "Number of processing threads to use")
    public int scanThreadCount = 5

    @Parameter(names = "-cache_scan_results", description = "Cache scan results to jnomad.cache file", arity = 1)
    public boolean cacheScanResults = true

    @Parameter(names = "-scan_file_limit", description = "Java source code file scan limit [-1 = disabled]")
    public int scanFileLimit = -1

    @Parameter(names = "-db_host", description = "Database host (Use : to specify host (ex. localhost:5432) [default = 5432])")
    public List<String> dbHost

    @Parameter(names = "-db_username", description = "Database username")
    public List<String> dbUsername

    @Parameter(names = "-db_password", description = "Database password")
    public List<String> dbPassword

    @Parameter(names = "-db_database", description = "Database name")
    public List<String> dbDatabase

    @Parameter(names = "-db_type", description = "Database type (Supported: PostgreSQL, MySQL)")
    public String databaseType = "PostgreSQL"

    @Parameter(names = "-analyze_explain", description = "Execute query explain with analyze (will actually run query)", arity = 1)
    public boolean queryExplainAnalyze = true

    @Parameter(names = "-offender_report_percent", description = "Report percentage of top offenders")
    public int offenderReportPercentage = 10

    @Parameter(names = "-index_priority_threshold", description = "Threshold index priority for recommendation")
    public int indexPriorityThreshold = 50

    @Parameter(names = ["-help", "--help"], description = "Displays help information")
    public boolean help

    @Parameter(names = ["-version", "--version"], description = "Displays version information")
    public boolean version

    //hack needed to suppress com.github.javaparser.symbolsolver.javaparsermodel.contexts.MethodCallExprContext
    //from writing to System.out; can remove when symbolsolver is upgraded to latest version.
    //todo: upgrade symbolsolver
    {
        PrintStream origOut = System.out
        PrintStream interceptor = new Interceptor(origOut)
        System.setOut(interceptor)
    }

    private static class Interceptor extends PrintStream {

        Interceptor(OutputStream out) {
            super(out, true)
        }

        @Override
        void print(String s) {
            if (s != null && !s.contains("APPLYING:")) {
                super.print(s)
            }
        }
    }

    static void main(String[] args) {
        JNomadCLI main = new JNomadCLI()
        def commander = null
        try {
            commander = new JCommander(main, args)
            commander.programName = "JNomadCLI"

            if (main.logFile != null) {
                System.setOut(new PrintStream(new BufferedOutputStream(new FileOutputStream(main.logFile)), true))
            }

            def sb = new StringBuilder()
            commander.usage(sb)
            if (main.help) {
                println sb.toString().replace("Options:", "Options:\n") //prettier usage output
                System.exit(0)
            }
            if (main.version) {
                println "JNomad - Version ${JNOMAD_VERSION} (Build: ${JNOMAD_BUILD_DATE})"
                System.exit(0)
            }
        } catch (ParameterException ex) {
            commander = new JCommander(main)
            commander.programName = "JNomadCLI"

            def sb = new StringBuilder()
            commander.usage(sb)
            println sb.toString().replace("Options:", "Options:\n") //prettier usage output
            ex.printStackTrace()
            System.exit(-1)
        }

        if (args == null || args.length == 0) {
            println("Invalid program arguments! Use -help to view valid arguments.")
            System.exit(-1)
        }

        long startTime = System.currentTimeMillis()

        JNomad jNomad = setupJNomadTask(main, commander)
        collectQueriesTask(jNomad)
        def queryParser = parseQueriesTask(jNomad)
        def indexReport = explainQueriesTask(jNomad, queryParser.aliasMap, DatabaseAdapterType.fromString(main.databaseType))
        reportQueriesTask(jNomad, indexReport)

        println "${breaker()}JNomad {${JNOMAD_VERSION}}: All tasks finished!\n\t\t - Total runtime: ${getRuntime(startTime)}${breaker()}"
    }

    private static QueryParser parseQueriesTask(JNomad jNomad) {
        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Parsing queries...${breaker()}"
        long startTime = System.currentTimeMillis()

        QueryParser queryParser = new QueryParser(jNomad)
        queryParser.run()

        def resultStr = ""
        resultStr += "\t\tTotal static queries: " + queryParser.totalStaticQueryCount + "\n"
        resultStr += "\t\tTotal dynamic queries: " + queryParser.totalDynamicQueryCount + "\n"
        resultStr += "\t\tUnique static queries: " + queryParser.uniqueStaticQueryCount + "\n"
        resultStr += "\t\tUnique dynamic queries: " + queryParser.uniqueDynamicQueryCount + "\n"
        resultStr += "\t\t-\n"
        resultStr += "\t\tSuccessfully parsed queries: " + queryParser.parsedQueryCount + "\n"
        resultStr += "\t\tUnsuccessfully parsed queries: " + queryParser.failedQueryCount

        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Task finished!\n${resultStr}\n\t\t - Task runtime: ${getRuntime(startTime)}${breaker()}"
        return queryParser
    }

    private static SourceCodeIndexReport explainQueriesTask(JNomad jNomad, QueryEntityAliasMap aliasMap, DatabaseAdapterType adapterType) {
        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Running PostgreSQL explains...${breaker()}"
        long startTime = System.currentTimeMillis()

        QueryIndexReport reportAdapter
        switch (adapterType) {
            case DatabaseAdapterType.MYSQL:
                reportAdapter = new MysqlQueryReport(jNomad, new MysqlDatabaseDataType(), aliasMap)
                break
            case DatabaseAdapterType.POSTGRES:
                reportAdapter = new PostgresQueryReport(jNomad, new PostgresDatabaseDataType(), aliasMap)
                break
        }

        def report = reportAdapter.createSourceCodeIndexReport(jNomad.scannedFileList)

        int allQueryCount = reportAdapter.allQueryList.size()
        int emptyWhereClauseCount = reportAdapter.emptyWhereClauseList.size()
        int failedParseCount = reportAdapter.failedQueryParseList.size()
        int missingRequiredTableCount = reportAdapter.missingRequiredTableList.size()
        int missingRequiredColumnCount = reportAdapter.missingRequiredColumnList.size()
        int successfullyExplainedCount = reportAdapter.successfullyExplainedQueryList.size()
        int permissionDeniedCount = reportAdapter.permissionDeniedTableList.size()

        def resultStr = "Queries attempted to explain: ${allQueryCount}\n"
        resultStr += "\t\tQueries sucessfully explained: ${successfullyExplainedCount}\n"
        resultStr += "\t\tQueries failed to explain: ${failedParseCount}\n"
        resultStr += "\t\tQueries failed due to permission denied: ${permissionDeniedCount}\n"
        resultStr += "\t\tQueries skipped due to missing WHERE clause: ${emptyWhereClauseCount}\n"
        resultStr += "\t\tQueries skipped due to missing required table(s): ${missingRequiredTableCount}\n"
        resultStr += "\t\tQueries skipped due to missing required column(s): ${missingRequiredColumnCount}"
        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Task finished!\n${resultStr}\n\t\t - Task runtime: ${getRuntime(startTime)}${breaker()}"
        return report
    }

    private static void collectQueriesTask(JNomad jNomad) {
        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Collecting queries...${breaker()}"
        long startTime = System.currentTimeMillis()

        jNomad.scanAllFiles()

        int foundQueryVariations = 0
        int foundDynamicQueryVariations = 0
        int scannedFileContainingQueryCount = 0
        for (SourceCodeExtract queryFile : jNomad.scannedFileList) {
            def queryExtractor = queryFile.getQueryLiteralExtractor()
            foundQueryVariations += queryExtractor.possibleQueryList.size()
            foundDynamicQueryVariations += queryExtractor.possibleDynamicQueryList.size()
            if (queryExtractor.queryFound) scannedFileContainingQueryCount++
        }

        def resultStr = "\t\tScanned files: ${jNomad.scannedFileList.size()}\n"
        resultStr += "\t\tFiles containing queries: ${scannedFileContainingQueryCount}\n"
        resultStr += "\t\tTotal queries found: ${foundQueryVariations + foundDynamicQueryVariations}"
        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Task finished!\n${resultStr}\n\t\t - Task runtime: ${getRuntime(startTime)}${breaker()}"
    }

    private static JNomad setupJNomadTask(JNomadCLI main, JCommander commander) {
        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Bootstrapping JNomad...${breaker()}"
        long startTime = System.currentTimeMillis()

        //JCommander doesn't provide argument values very easily...
        commander.parameters.each {
            if (it.assigned) {
                def val = null
                switch (it.field.name) {
                    case "logFile":
                        val = main.logFile
                        break
                    case "sourceDirectoryList":
                        val = main.sourceDirectoryList.toListString()
                        break
                    case "scanFileList":
                        val = main.scanFileList
                        break
                    case "scanDirectoryList":
                        val = main.scanDirectoryList.toListString()
                        break
                    case "recursiveDirectoryScan":
                        val = main.recursiveDirectoryScan
                        break
                    case "sourceDirectoryPrescan":
                        val = main.sourceDirectoryPrescan
                        break
                    case "scanThreadCount":
                        val = main.scanThreadCount
                        break
                    case "cacheScanResults":
                        val = main.cacheScanResults
                        break
                    case "dbHost":
                        val = main.dbHost.toListString()
                        break
                    case "dbUsername":
                        val = main.dbUsername.toListString()
                        break
                    case "dbPassword":
                        val = main.dbPassword.toListString()
                        break
                    case "dbDatabase":
                        val = main.dbDatabase.toListString()
                        break
                    case "queryExplainAnalyze":
                        val = main.queryExplainAnalyze
                        break
                    case "offenderReportPercentage":
                        val = main.offenderReportPercentage
                        break
                    case "indexPriorityThreshold":
                        val = main.indexPriorityThreshold
                        break
                }
                println "Applied non-default argument: " + it.field.name + " - " + val
            }
        }

        //setup main type solver
        SourceCodeTypeSolver typeSolver = new SourceCodeTypeSolver()

        //.Java source directories
        def tempRemoveList = new ArrayList<>()
        def tempAddList = new ArrayList<>()
        for (String sourceDirectory : MoreObjects.firstNonNull(main.sourceDirectoryList, ImmutableList.<String> of())) {
            def srcMainJavaDir = new File(sourceDirectory, "src/main/java")
            if (srcMainJavaDir.exists()) {
                tempRemoveList.add(sourceDirectory)
                sourceDirectory = srcMainJavaDir
                tempAddList.add(sourceDirectory)
            }

            addJavaParserTypeSolver(typeSolver, sourceDirectory, false)
        }
        if (main.sourceDirectoryList != null) {
            main.sourceDirectoryList.removeAll(tempRemoveList)
            main.sourceDirectoryList.addAll(tempAddList)
        }

        //setup JNomad instance
        JNomad jNomad = new JNomad(typeSolver)
        for (String scanFile : MoreObjects.firstNonNull(main.scanFileList, ImmutableList.<String> of())) {
            jNomad.addScanFile(scanFile)
        }
        for (String scanDirectory : MoreObjects.firstNonNull(main.scanDirectoryList, ImmutableList.<String> of())) {
            jNomad.addScanDirectory(scanDirectory)

            if (main.sourceDirectoryPrescan) {
                List<File> queue = new ArrayList<>()
                CodeLocator.findSourceDirectories(new File(scanDirectory), queue, main.recursiveDirectoryScan)
                queue.each {
                    if (!MoreObjects.firstNonNull(main.sourceDirectoryList, ImmutableList.<String> of()).contains(it.absolutePath)) {
                        addJavaParserTypeSolver(typeSolver, it.absolutePath, true)
                    }
                }
            }
        }

        jNomad.recursiveDirectoryScan = main.recursiveDirectoryScan
        jNomad.scanThreadCount = main.scanThreadCount
        jNomad.scanFileLimit = main.scanFileLimit
        jNomad.cacheScanResults = main.cacheScanResults
        jNomad.dbHost.addAll(main.dbHost)
        jNomad.dbUsername.addAll(main.dbUsername)
        jNomad.dbPassword.addAll(main.dbPassword)
        jNomad.dbDatabase.addAll(main.dbDatabase)
        jNomad.queryExplainAnalyze = main.queryExplainAnalyze
        jNomad.offenderReportPercentage = main.offenderReportPercentage
        jNomad.indexPriorityThreshold = main.indexPriorityThreshold

        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Task finished!\n\t\t - Task runtime: ${getRuntime(startTime)}${breaker()}"
        return jNomad
    }

    private static void reportQueriesTask(JNomad jNomad, SourceCodeIndexReport indexReport) {
        def totalRuntimeStats = new DescriptiveStatistics()
        def executionTimeStats = new DescriptiveStatistics()
        def totalCostStats = new DescriptiveStatistics()
        def sequenceScanStats = new DescriptiveStatistics()

        if (!indexReport.totalRuntimeMap.isEmpty()) {
            println "${breaker()}JNomad {${JNOMAD_VERSION}}: Total Runtime Queries - Top ${jNomad.offenderReportPercentage}% Offenders${breaker()}"
            outputQueryScores(jNomad.offenderReportPercentage, indexReport.totalRuntimeMap, totalRuntimeStats)
        }
        if (!indexReport.executionTimeMap.isEmpty()) {
            println "${breaker()}JNomad {${JNOMAD_VERSION}}: Execution Time Queries - Top ${jNomad.offenderReportPercentage}% Offenders${breaker()}"
            outputQueryScores(jNomad.offenderReportPercentage, indexReport.executionTimeMap, executionTimeStats)
        }
        if (!indexReport.totalCostMap.isEmpty()) {
            println "${breaker()}JNomad {${JNOMAD_VERSION}}: Total Cost Queries - Top ${jNomad.offenderReportPercentage}% Offenders${breaker()}"
            outputQueryScores(jNomad.offenderReportPercentage, indexReport.totalCostMap, totalCostStats)
        }
        if (!indexReport.sequenceScanMap.isEmpty()) {
            println "${breaker()}JNomad {${JNOMAD_VERSION}}: Sequence Scan Queries - Top ${jNomad.offenderReportPercentage}% Offenders${breaker()}"
            outputQueryScores(jNomad.offenderReportPercentage, indexReport.sequenceScanMap, sequenceScanStats)
        }

        //finally, recommend some indexes
        println "${breaker()}JNomad {${JNOMAD_VERSION}}: Index Recommendations${breaker()}"
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
                println "\nIndex: " + "CREATE INDEX ON ${it.value.tableName} (${it.value.toIndex.toString()});"
                println "\tIndex Priority: " + Precision.round(it.key, 0)
                println "\tIndex Table: " + it.value.tableName
                println "\tIndex Condition: " + it.value.toIndex.toString()

                //unique query locations this index would likely improve
                def locationSet = new HashSet<>()
                boolean outputHeader = false
                it.value.hitList.each {
                    def literalExtractor = it.explainResult.sourceCodeExtract.queryLiteralExtractor
                    def range = literalExtractor.getQueryCallRange(it.explainResult.originalQuery)

                    if (!locationSet.contains(range)) {
                        if (!outputHeader) {
                            println("\tIndex Affects:")
                            outputHeader = true
                        }
                        println "\t\tFile: " + literalExtractor.sourceFile + " - Location: " + range
                    }
                    locationSet.add(range)
                }
                println()
                recommendationCount++
            }
        }

        if (recommendationCount == 0) {
            println "There are no available query index recommendations. Good job!"
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

    private static
    void outputQueryScores(int offenderReportPercentage, TreeMap<Double, List<PostgresExplain>> reportMap, DescriptiveStatistics stats) {
        int totalCount = reportMap.size()
        int reportCount = (offenderReportPercentage / 100) * totalCount
        int index = 0
        def top = reportMap.pollLastEntry()
        boolean output = true
        while (top != null) {
            stats.addValue(top.key)
            if (output) {
                for (PostgresExplain explain : top.value) {
                    println "Score: " + top.key
                    println "\tOriginal query: " + explain.originalQuery
                    println "\tExplained query: " + explain.finalQuery
                    println "\tSource code file: " + explain.sourceCodeExtract.queryLiteralExtractor.getClassName()
                    //println "\tExplain: " + top.value.toString()
                }
            }

            output = !(index++ >= reportCount)
            top = reportMap.pollLastEntry()
            if (top != null && output) println()
        }
    }

    private static String getRuntime(long startTime) {
        long runTime = System.currentTimeMillis() - startTime
        return String.format("%d min, %d sec",
                TimeUnit.MILLISECONDS.toMinutes(runTime),
                TimeUnit.MILLISECONDS.toSeconds(runTime) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(runTime)))
    }

    private static String breaker() {
        StringBuilder sb = new StringBuilder("\n")
        for (int i = 0; i < 100; i++) {
            sb.append("*")
        }
        sb.append("\n")
        return sb.toString()
    }

    private static
    void addJavaParserTypeSolver(SourceCodeTypeSolver typeSolver, String sourceDirectoryLocation, boolean autoScan) {
        typeSolver.addJavaParserTypeSolver(new File(sourceDirectoryLocation))
        if (autoScan) {
            println "Added source code directory (auto-scan): " + sourceDirectoryLocation
        } else {
            println "Added source code directory: " + sourceDirectoryLocation
        }
    }

}
