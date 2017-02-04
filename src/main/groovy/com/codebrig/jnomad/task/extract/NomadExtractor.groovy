package com.codebrig.jnomad.task.extract

import com.codebrig.jnomad.model.SourceCodeExtract
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.mapdb.DB
import org.mapdb.Serializer

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
abstract class NomadExtractor extends VoidVisitorAdapter<JavaParserFacade> {

    public final File sourceFile
    public final TypeSolver typeSolver
    public CompilationUnit compilationUnit
    private String qualifiedClassName
    protected final DB cache
    protected boolean skipScan

    NomadExtractor(CompilationUnit compilationUnit, File sourceFile, TypeSolver typeSolver, DB cache) {
        this.compilationUnit = compilationUnit
        this.sourceFile = Objects.requireNonNull(sourceFile)
        this.typeSolver = Objects.requireNonNull(typeSolver)
        this.cache = cache

        qualifiedClassName = Files.getNameWithoutExtension(sourceFile.name)
        if (compilationUnit.package.isPresent()) {
            qualifiedClassName = compilationUnit.package.get().name.qualifiedName + "." + qualifiedClassName
        }
    }

    String getQualifiedClassName() {
        return qualifiedClassName
    }

    String getClassName() {
        return Files.getNameWithoutExtension(sourceFile.name)
    }

    String getFileCrc32() {
        Files.hash(sourceFile, Hashing.crc32())
    }

    long getFileSize() {
        return sourceFile.length()
    }

    String getFileChecksum() {
        return getFileCrc32() + "-" + getFileSize() + className
    }

    abstract String getName()

    abstract void loadCache()

    abstract void scan(final SourceCodeExtract sourceCodeExtract)

    abstract void saveCache()

    def getStorableStringStringMap(String mapName) {
        return cache.hashMap(getName() + "_" + mapName, Serializer.STRING, Serializer.STRING).createOrOpen()
    }

    boolean isUsingCache() {
        return cache != null
    }

}
