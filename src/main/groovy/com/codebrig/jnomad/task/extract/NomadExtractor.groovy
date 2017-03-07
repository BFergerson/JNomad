package com.codebrig.jnomad.task.extract

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.utils.CodeLocator
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.type.ClassOrInterfaceType
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
    private String qualifiedClassName
    protected final DB cache
    protected boolean skipScan
    private String fileChecksum
    private List<ClassOrInterfaceType> extendsClassPath

    NomadExtractor(CompilationUnit compilationUnit, File sourceFile, TypeSolver typeSolver, DB cache) {
        this.sourceFile = Objects.requireNonNull(sourceFile)
        this.typeSolver = Objects.requireNonNull(typeSolver)
        this.cache = cache

        qualifiedClassName = Files.getNameWithoutExtension(sourceFile.name)
        if (compilationUnit.package.isPresent()) {
            qualifiedClassName = compilationUnit.package.get().name.qualifiedName + "." + qualifiedClassName
        }

        extendsClassPath = new ArrayList<>()
        if (compilationUnit.types != null && !compilationUnit.types.isEmpty()) {
            def classDeclaration = CodeLocator.findClassOrInterfaceDeclarationExpression(compilationUnit.types.get(0))
            if (classDeclaration != null) {
                extendsClassPath.addAll(CodeLocator.locateClassExtensionPath(classDeclaration))
            }
        }
    }

    List<ClassOrInterfaceType> getExtendsClassPath() {
        return extendsClassPath
    }

    String getQualifiedClassName() {
        return qualifiedClassName
    }

    String getClassName() {
        return Files.getNameWithoutExtension(sourceFile.name)
    }

    String getFileCrc32() {
        return Files.hash(sourceFile, Hashing.crc32())
    }

    long getFileSize() {
        return sourceFile.length()
    }

    String getFileChecksum() {
        if (fileChecksum == null) {
            fileChecksum = getFileCrc32() + "-" + getFileSize() + className
        }
        return fileChecksum
    }

    abstract String getName()

    abstract void loadCache()

    abstract void scan(final SourceCodeExtract sourceCodeExtract, final CompilationUnit compilationUnit)

    abstract void saveCache()

    def getStorableStringStringMap(String mapName) {
        return cache.hashMap(getName() + "_" + mapName, Serializer.STRING, Serializer.STRING).createOrOpen()
    }

    boolean isUsingCache() {
        return cache != null
    }

}
