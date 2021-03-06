package com.codebrig.jnomad.tests;

import com.codebrig.jnomad.JNomad;
import com.codebrig.jnomad.SourceCodeTypeSolver;
import com.codebrig.jnomad.model.*;
import com.codebrig.jnomad.task.explain.adapter.DatabaseAdapterType;
import com.github.javaparser.Range;

import java.io.File;
import java.util.*;

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
public class ScanSingleFile {

    public static void main(String[] args) {
        //setup main type solver
        SourceCodeTypeSolver typeSolver = new SourceCodeTypeSolver();

        //.Java source directories
        List<String> sourceDirectoryList = new ArrayList<>();

        //todo: add source directory of (from project workspace)
        //sourceDirectoryList.add(new File("").getAbsoluteFile().getAbsolutePath());

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
        jNomad.getDbHost().add("localhost");
        jNomad.getDbDatabase().add("test");
        jNomad.getDbUsername().add("root");
        jNomad.getDbPassword().add("");

        //so it will recommend an index on everything and report on everything (for testing)
        jNomad.setIndexPriorityThreshold(0);
        jNomad.setOffenderReportPercentage(100);

        //scan file
        File scanFile = new File("src\\test\\resources\\TestSingleFile.java");
        System.out.println("Scanning file: " + scanFile + "\n");
        SourceCodeExtract extract = jNomad.scanSingleFile(scanFile);

        //output results
        System.out.println("\nFound query/queries: " + extract.getQueryLiteralExtractor().getQueryFound());
        if (extract.getQueryLiteralExtractor().getQueryFound()) {
            FileFullReport fileFullReport = new FileFullReport(scanFile, jNomad, DatabaseAdapterType.MYSQL);

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

            if (fileFullReport.getRecommendedIndexList().isEmpty()) {
                System.out.println("There are no available query index recommendations. Good job!");
            }
        }
    }

}
