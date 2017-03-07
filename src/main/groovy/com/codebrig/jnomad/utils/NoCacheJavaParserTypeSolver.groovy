package com.codebrig.jnomad.utils

import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.symbolsolver.javaparser.Navigator
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder

import java.util.concurrent.TimeUnit

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class NoCacheJavaParserTypeSolver implements TypeSolver {

    private File srcDir
    private TypeSolver parent

    NoCacheJavaParserTypeSolver(File srcDir) {
        this.srcDir = srcDir
    }

    @Override
    String toString() {
        return "NoCacheJavaParserTypeSolver{" +
                "srcDir=" + srcDir +
                ", parent=" + parent +
                '}'
    }

    @Override
    TypeSolver getParent() {
        return parent
    }

    @Override
    void setParent(TypeSolver parent) {
        this.parent = parent
    }

    @Override
    SymbolReference<TypeDeclaration> tryToSolveType(String name) {
        if (!srcDir.exists() || !srcDir.isDirectory()) {
            throw new IllegalStateException("SrcDir does not exist or is not a directory: " + srcDir.getAbsolutePath())
        }

        // TODO support enums
        // TODO support interfaces

        String[] nameElements = name.split("\\.")
        for (int i = nameElements.length; i > 0; i--) {
            String filePath = srcDir.getAbsolutePath()
            for (int j = 0; j < i; j++) {
                filePath += "/" + nameElements[j]
            }
            filePath += ".java"

            File srcFile = new File(filePath)
            if (srcFile.exists()) {
                try {
                    String typeName = ""
                    for (int j = i - 1; j < nameElements.length; j++) {
                        if (j != i - 1) {
                            typeName += "."
                        }
                        typeName += nameElements[j]
                    }

                    CompilationUnit compilationUnit = JavaParser.parse(srcFile)
                    Optional<com.github.javaparser.ast.body.TypeDeclaration<?>> astTypeDeclaration = Navigator.findType(compilationUnit, typeName)
                    if (!astTypeDeclaration.isPresent()) {
                        return SymbolReference.unsolved(TypeDeclaration.class)
                    }
                    TypeDeclaration typeDeclaration = JavaParserFacade.get(this).getTypeDeclaration(astTypeDeclaration.get())
                    return SymbolReference.solved(typeDeclaration)
                } catch (IOException e) {
                    throw new RuntimeException(e)
                }
            }
        }

        return SymbolReference.unsolved(TypeDeclaration.class)
    }

}
