package com.codebrig.jnomad.task.extract

import com.codebrig.jnomad.model.SourceCodeExtract
import com.github.javaparser.ast.CompilationUnit

/**
 * This class will visit all Java source code files and determine if they
 * utilize javax.persistence.Query. Those that do are given to QueryExpressionResolver for further processing.
 *
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class SourceCodeExtractRunner {

    private final List<NomadExtractor> extractorList = new ArrayList<>()
    private final SourceCodeExtract sourceCodeExtract

    SourceCodeExtractRunner(NomadExtractor extractor, NomadExtractor... extractors) {
        if (extractor == null || Arrays.asList(extractors).contains(null)) {
            throw new IllegalArgumentException("Missing or invalid nomad extractor(s)!")
        } else if (extractors.length > 0) {
            extractors.each {
                extractorList.add(it)
            }
        }

        extractorList.add(0, extractor)
        this.sourceCodeExtract = new SourceCodeExtract(extractorList.toArray(new NomadExtractor[0]))
    }

    void scan(CompilationUnit compilationUnit) {
        for (NomadExtractor extractor : extractorList) {
            if (extractor.isUsingCache()) {
                extractor.loadCache()
            }

            if (!extractor.skipScan) {
                extractor.scan(sourceCodeExtract, compilationUnit)

                if (extractor.isUsingCache()) {
                    extractor.saveCache()
                }
            }
        }
    }

    SourceCodeExtract getSourceCodeExtract() {
        return sourceCodeExtract
    }

}
