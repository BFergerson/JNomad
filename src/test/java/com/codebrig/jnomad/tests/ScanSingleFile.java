package com.codebrig.jnomad.tests;

import com.codebrig.jnomad.JNomad;
import com.codebrig.jnomad.SourceCodeTypeSolver;
import com.codebrig.jnomad.model.*;
import com.codebrig.jnomad.task.explain.adapter.postgres.PostgresQueryReport;
import com.codebrig.jnomad.task.parse.QueryParser;
import com.github.javaparser.Range;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JreTypeSolver;

import java.io.File;
import java.util.*;

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
public class ScanSingleFile {

    public static void main(String[] args) {
        //setup main type solver
        SourceCodeTypeSolver typeSolver = new SourceCodeTypeSolver();
        typeSolver.add(new JreTypeSolver());

        //.Java source directories
        List<String> sourceDirectoryList = new ArrayList<>();
        sourceDirectoryList.add(new File("").getAbsoluteFile().getAbsolutePath());

        List<String> tempRemoveList = new ArrayList<>();
        List<String> tempAddList = new ArrayList<>();
        for (String sourceDirectory : sourceDirectoryList) {
            File srcMainJavaDir = new File(sourceDirectory, "src/main/java");
            if (srcMainJavaDir.exists()) {
                tempRemoveList.add(sourceDirectory);
                sourceDirectory = srcMainJavaDir.getAbsolutePath();
                tempAddList.add(sourceDirectory);
            }


            typeSolver.addJavaParserTypeSolver(new File(sourceDirectory));
        }
        sourceDirectoryList.removeAll(tempRemoveList);
        sourceDirectoryList.addAll(tempAddList);

        //setup JNomad instance
        JNomad jNomad = new JNomad(typeSolver);

        //db access
        jNomad.getDbHost().add("localhost:5433");
        jNomad.getDbDatabase().add("test");
        jNomad.getDbUsername().add("postgres");
        jNomad.getDbPassword().add("postgres");

        //so it will recommend an index on everything and report on everything (for testing)
        jNomad.setIndexPriorityThreshold(0);
        jNomad.setOffenderReportPercentage(100);

        //scan file
        File scanFile = new File("C:\\Users\\Brandon\\IdeaProjects\\JNomad\\src\\test\\resources\\TestSingleFile.java");
        System.out.println("Scanning file: " + scanFile + "\n");
        SourceCodeExtract extract = jNomad.scanSingleFile(scanFile);

        //output results
        System.out.println("\nFound query/queries: " + extract.getQueryLiteralExtractor().getQueryFound());
        if (extract.getQueryLiteralExtractor().getQueryFound()) {
            QueryParser queryParser = new QueryParser(jNomad);
            queryParser.run();

            PostgresQueryReport reportAdapter = new PostgresQueryReport(jNomad, queryParser);
            SourceCodeIndexReport report = reportAdapter.createSourceCodeIndexReport();

            FileFullReport fileFullReport = new FileFullReport(scanFile, jNomad, report);

            System.out.println("\nQuery scores:");
            for (QueryScore queryScore : fileFullReport.getQueryScoreList()) {
                System.out.println("\n\tScore: " + queryScore.getScore());
                System.out.println("\tOriginal query: " + queryScore.getOriginalQuery());
                System.out.println("\tExplained query: " + queryScore.getExplainedQuery());
                System.out.println("\tFile: " + queryScore.getQueryFile() + " - Location: " + queryScore.getQueryLocation());
            }

            System.out.println("\nRecommended indexes:");
            for (RecommendedIndex rIndex : fileFullReport.getRecommendedIndexList()) {
                System.out.println("\n\tIndex: " + rIndex.getIndexCreateSQL());
                System.out.println("\tIndex Priority: " + rIndex.getIndexPriority());
                System.out.println("\tIndex Table: " + rIndex.getIndexTable());
                System.out.println("\tIndex Condition: " + rIndex.getIndexCondition());
                System.out.println("\tIndex Affects: ");

                for (Map.Entry<File, Range> entry : rIndex.getIndexAffectMap().entrySet()) {
                    System.out.println("\t\tFile: " + entry.getKey() + " - Location: " + entry.getValue());
                }
            }
        }
    }

}
