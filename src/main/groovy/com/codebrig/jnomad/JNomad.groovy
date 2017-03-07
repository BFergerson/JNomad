package com.codebrig.jnomad

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.extract.SourceCodeExtractRunner
import com.codebrig.jnomad.task.extract.extractor.query.*
import com.codebrig.jnomad.utils.CodeLocator
import com.github.javaparser.JavaParser
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.google.common.util.concurrent.Runnables
import org.mapdb.DB
import org.mapdb.DBMaker

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Scans given Java source code files for imports of javax.persistence.Query
 * which are resolved to extract any possible queries.
 *
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class JNomad {

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

    private List<String> scanFileList = new CopyOnWriteArrayList<>()
    private List<String> scanDirectoryList = new CopyOnWriteArrayList<>()
    private boolean recursiveDirectoryScan = true
    private int scanThreadCount = 5
    private int scanFileLimit = -1
    private boolean cacheScanResults = true

    //todo: make private
    public List<String> dbHost
    public List<String> dbUsername
    public List<String> dbPassword
    public List<String> dbDatabase
    public boolean queryExplainAnalyze
    public int offenderReportPercentage = 10
    public int indexPriorityThreshold = 50

    private TypeSolver typeSolver
    private Set<File> scannedFileSet = new HashSet<>()
    private List<SourceCodeExtract> scannedFileList = new CopyOnWriteArrayList<>()
    private int begunScanCount = 0
    private DB cache

    JNomad(TypeSolver typeSolver) {
        this.typeSolver = typeSolver
    }

    def run() {
        ExecutorService executorService = Executors.newFixedThreadPool(scanThreadCount)
        if (cacheScanResults) {
            cache = DBMaker.fileDB("jnomad.cache").make() //todo: configurable cache
        }

        //scan files requested
        for (String sourceFile : scanFileList) {
            if (scanFileLimit == -1 || begunScanCount < scanFileLimit) {
                def scanFileTask = scanFile(new File(sourceFile), typeSolver)
                if (scanFileTask != Runnables.doNothing()) {
                    executorService.submit(scanFileTask)
                }
            }
        }

        //scan directories requested
        for (String sourceDirectory : scanDirectoryList) {
            for (final File sourceFile : CodeLocator.findSourceFiles(new File(sourceDirectory), new ArrayList<File>(), true)) {
                if (scanFileLimit == -1 || begunScanCount < scanFileLimit) {
                    def scanFileTask = scanFile(sourceFile, typeSolver)
                    if (scanFileTask != Runnables.doNothing()) {
                        executorService.submit(scanFileTask)
                    }
                }
            }
        }

        //wait for everything to settle
        executorService.shutdown()
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
        } catch (InterruptedException e) {
            e.printStackTrace()
        }

        if (cache != null) cache.close()
    }

    private Runnable scanFile(final File f, final TypeSolver typeSolver) {
        if (scannedFileSet.contains(f)) {
            return Runnables.doNothing()
        } else {
            scannedFileSet.add(f)

            return new Runnable() {
                void run() {
                    begunScanCount++
                    if (scanFileLimit != -1 && begunScanCount > scanFileLimit) {
                        return //hit scan file limit
                    }

                    try {
                        //println "Scanning source code file: " + f.getName()
                        def compilationUnit = JavaParser.parse(f)
                        def queryLiteral = new QueryLiteralExtractor(compilationUnit, f, typeSolver, cache)
                        def queryTable = new QueryTableAliasExtractor(compilationUnit, f, typeSolver, cache)
                        def queryColumn = new QueryColumnAliasExtractor(compilationUnit, f, typeSolver, cache)
                        def queryColumnDataType = new QueryColumnDataTypeExtractor(compilationUnit, f, typeSolver, cache)
                        def queryColumnJoin = new QueryColumnJoinExtractor(compilationUnit, f, typeSolver, cache)

                        SourceCodeExtractRunner sourceCodeVisitor = new SourceCodeExtractRunner(
                                queryLiteral, queryTable, queryColumn, queryColumnDataType, queryColumnJoin)
                        sourceCodeVisitor.scan(compilationUnit)
                        scannedFileList.add(sourceCodeVisitor.sourceCodeExtract)
                    } catch (Exception ex) {
                        ex.printStackTrace() //make sure we don't lose any exceptions thrown in runnable
                    }
                }
            }
        }
    }

    List<SourceCodeExtract> getScannedFileList() {
        return scannedFileList
    }

    void addScanFile(String scanFile) {
        this.scanFileList.add(Objects.requireNonNull(scanFile))
    }

    void setScanFileList(List<String> scanFileList) {
        this.scanFileList = Objects.requireNonNull(scanFileList)
    }

    void addScanDirectory(String scanDirectory) {
        this.scanDirectoryList.add(Objects.requireNonNull(scanDirectory))
    }

    void setScanDirectoryList(List<String> scanDirectoryList) {
        this.scanDirectoryList = Objects.requireNonNull(scanDirectoryList)
    }

    void setRecursiveDirectoryScan(boolean recursiveDirectoryScan) {
        this.recursiveDirectoryScan = recursiveDirectoryScan
    }

    void setScanThreadCount(int scanThreadCount) {
        this.scanThreadCount = scanThreadCount
    }

    int getScanThreadCount() {
        return scanThreadCount
    }

    void setScanFileLimit(int scanFileLimit) {
        this.scanFileLimit = scanFileLimit
    }

    int getScanFileLimit() {
        return scanFileLimit
    }

    void setCacheScanResults(boolean cacheScanResults) {
        this.cacheScanResults = cacheScanResults
    }

    boolean getCacheScanResults() {
        return cacheScanResults
    }

    List<String> getDbHost() {
        return dbHost
    }

    List<String> getDbUsername() {
        return dbUsername
    }

    List<String> getDbPassword() {
        return dbPassword
    }

    List<String> getDbDatabase() {
        return dbDatabase
    }

    TypeSolver getTypeSolver() {
        return typeSolver
    }

}
