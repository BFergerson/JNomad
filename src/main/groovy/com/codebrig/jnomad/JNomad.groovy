package com.codebrig.jnomad

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.extract.SourceCodeExtractRunner
import com.codebrig.jnomad.task.extract.extractor.query.*
import com.codebrig.jnomad.utils.CodeLocator
import com.github.javaparser.JavaParser
import com.google.common.io.Files
import com.google.common.io.Resources
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

    private List<String> scanFileList = new CopyOnWriteArrayList<>()
    private List<String> scanDirectoryList = new CopyOnWriteArrayList<>()
    private boolean recursiveDirectoryScan = true
    private int scanThreadCount = 5
    private int scanFileLimit = -1
    private boolean cacheScanResults = true
    private List<String> dbHost
    private List<String> dbUsername
    private List<String> dbPassword
    private List<String> dbDatabase
    private String databaseType = "PostgreSQL"
    private boolean queryExplainAnalyze
    private int offenderReportPercentage = 10
    private int indexPriorityThreshold = 50
    private SourceCodeTypeSolver typeSolver
    private Set<File> scannedFileSet = new HashSet<>()
    private List<SourceCodeExtract> scannedFileList = new CopyOnWriteArrayList<>()
    private int begunScanCount = 0
    private DB cache

    JNomad(SourceCodeTypeSolver typeSolver) {
        this.typeSolver = typeSolver

        URL javaEEUrl = null
        try {
            //todo: improve this whole block
            //add JavaEE API to type solver
            def domain = JNomad.class.getProtectionDomain()
            javaEEUrl = domain.getClassLoader().getResource("javaee-api-7.0.jar")
            if (javaEEUrl == null) {
                javaEEUrl = domain.getClassLoader().getResource("/javaee-api-7.0.jar")
            }

            if (javaEEUrl != null) {
                def path = javaEEUrl.toString().replace("%20", " ")
                if (path.startsWith("file:/")) {
                    path = path.substring(6)
                }
                typeSolver.addJarTypeSolver(path)
            } else {
                def f = new File(domain.getCodeSource().getLocation().toURI().getPath())
                if (f.isDirectory()) {
                    def path = getClass().getResource("/javaee-api-7.0.jar").toExternalForm()
                    if (path.startsWith("file:/")) {
                        path = path.substring(6)
                    }
                    typeSolver.addJarTypeSolver(path)
                } else {
                    typeSolver.addJarTypeSolver(f.absolutePath)
                }
            }
        } catch (Exception ex) {
            if (javaEEUrl != null) {
                //fall back to extracting the javaee-api-7.0.jar and use that
                byte[] bytes = Resources.toByteArray(javaEEUrl)
                File tmpFile = new File(System.getProperty("java.io.tmpdir"), "javaee-api-7.0.jar")
                Files.write(bytes, tmpFile)
                typeSolver.addJarTypeSolver(tmpFile.absolutePath)
            } else {
                ex.printStackTrace()
            }
        }
    }

    def scanAllFiles() {
        ExecutorService executorService = Executors.newFixedThreadPool(scanThreadCount)
        if (cacheScanResults) {
            try {
                cache = DBMaker.fileDB(System.getProperty("java.io.tmpdir") + "jnomad.cache").make() //todo: configurable cache
            } catch(Exception e) {
                new File(System.getProperty("java.io.tmpdir") + "jnomad.cache").delete()
                cache = DBMaker.fileDB(System.getProperty("java.io.tmpdir") + "jnomad.cache").make() //todo: configurable cache
            }
        }

        //scan files requested
        for (String sourceFile : scanFileList) {
            if (scanFileLimit == -1 || begunScanCount < scanFileLimit) {
                def scanFileTask = scanFile(new File(sourceFile))
                if (scanFileTask != Runnables.doNothing()) {
                    executorService.submit(scanFileTask)
                }
            }
        }

        //scan directories requested
        for (String sourceDirectory : scanDirectoryList) {
            for (final File sourceFile : CodeLocator.findSourceFiles(new File(sourceDirectory), new ArrayList<File>(), true)) {
                if (scanFileLimit == -1 || begunScanCount < scanFileLimit) {
                    def scanFileTask = scanFile(sourceFile)
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

    SourceCodeExtract scanSingleFile(File file) {
        def compilationUnit = JavaParser.parse(file)
        def queryLiteral = new QueryLiteralExtractor(compilationUnit, file, typeSolver, cache)
        def queryTable = new QueryTableAliasExtractor(compilationUnit, file, typeSolver, cache)
        def queryColumn = new QueryColumnAliasExtractor(compilationUnit, file, typeSolver, cache)
        def queryColumnDataType = new QueryColumnDataTypeExtractor(compilationUnit, file, typeSolver, cache)
        def queryColumnJoin = new QueryColumnJoinExtractor(compilationUnit, file, typeSolver, cache)

        SourceCodeExtractRunner sourceCodeVisitor = new SourceCodeExtractRunner(
                queryLiteral, queryTable, queryColumn, queryColumnDataType, queryColumnJoin)
        sourceCodeVisitor.scan(compilationUnit)
        scannedFileList.add(sourceCodeVisitor.sourceCodeExtract)
        return sourceCodeVisitor.sourceCodeExtract
    }

    private Runnable scanFile(final File f) {
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
                        scanSingleFile(f)
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
        if (dbHost == null) {
            dbHost = new ArrayList<>()
        }
        return dbHost
    }

    List<String> getDbUsername() {
        if (dbUsername == null) {
            dbUsername = new ArrayList<>()
        }
        return dbUsername
    }

    List<String> getDbPassword() {
        if (dbPassword == null) {
            dbPassword = new ArrayList<>()
        }
        return dbPassword
    }

    List<String> getDbDatabase() {
        if (dbDatabase == null) {
            dbDatabase = new ArrayList<>()
        }
        return dbDatabase
    }

    String getDatabaseType() {
        return databaseType
    }

    void setDatabaseType(String databaseType) {
        this.databaseType = databaseType
    }

    SourceCodeTypeSolver getTypeSolver() {
        return typeSolver
    }

    void setQueryExplainAnalyze(boolean queryExplainAnalyze) {
        this.queryExplainAnalyze = queryExplainAnalyze
    }

    boolean getQueryExplainAnalyze() {
        return queryExplainAnalyze
    }

    void setOffenderReportPercentage(int offenderReportPercentage) {
        this.offenderReportPercentage = offenderReportPercentage
    }

    int getOffenderReportPercentage() {
        return offenderReportPercentage
    }

    void setIndexPriorityThreshold(int indexPriorityThreshold) {
        this.indexPriorityThreshold = indexPriorityThreshold
    }

    int getIndexPriorityThreshold() {
        return indexPriorityThreshold
    }

}
