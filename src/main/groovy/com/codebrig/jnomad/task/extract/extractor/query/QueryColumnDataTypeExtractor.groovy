package com.codebrig.jnomad.task.extract.extractor.query

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.extract.NomadExtractor
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.imports.ImportDeclaration
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import org.mapdb.DB

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryColumnDataTypeExtractor extends NomadExtractor {

    private Map<String, String> columnDataTypeMap

    QueryColumnDataTypeExtractor(CompilationUnit compilationUnit, File sourceFile, TypeSolver typeSolver, DB db) {
        super(compilationUnit, sourceFile, typeSolver, db)
        columnDataTypeMap = new HashMap<>()
    }

    @Override
    String getName() {
        return getClass().name
    }

    @Override
    void loadCache() {
        def fileHashMap = getStorableStringStringMap("file_hash_map")
        if (fileHashMap.containsKey(getFileChecksum())) {
            //already scanned this file
            String storedString = fileHashMap.get(getFileChecksum())
            columnDataTypeMap = new ObjectMapper().readValue(storedString,
                    new TypeReference<HashMap<String, String>>() {})
            skipScan = true
        }
    }

    @Override
    void saveCache() {
        def fileHashMap = getStorableStringStringMap("file_hash_map")
        def writer = new StringWriter()
        new ObjectMapper().writeValue(writer, columnDataTypeMap)
        fileHashMap.put(getFileChecksum(), writer.toString())
    }

    @Override
    void visit(final ClassOrInterfaceDeclaration n, final JavaParserFacade javaParser) {
        n.members.childrenNodes.each {
            if (it instanceof FieldDeclaration) {
                for (def annotation : it.annotations) {
                    if (annotation.name.name == "JoinColumn") {
                        if (annotation instanceof NormalAnnotationExpr) {
                            for (def pair : annotation.pairs) {
                                if (pair.name == "name") {
                                    def qualifiedColumnName = pair.value.toStringWithoutComments().toLowerCase().replace("\"", "")
                                    if (it.elementType instanceof ClassOrInterfaceType) {
                                        ClassOrInterfaceType type = (ClassOrInterfaceType) it.elementType
                                        if (!columnDataTypeMap.containsKey(qualifiedColumnName)) {
                                            columnDataTypeMap.put(qualifiedColumnName, type.name)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (annotation.name.name == "Column") {
                        if (annotation instanceof NormalAnnotationExpr) {
                            for (def pair : annotation.pairs) {
                                if (pair.name == "name") {
                                    def qualifiedColumnName = pair.value.toStringWithoutComments().toLowerCase().replace("\"", "")
                                    if (it.elementType instanceof ClassOrInterfaceType) {
                                        ClassOrInterfaceType type = (ClassOrInterfaceType) it.elementType
                                        columnDataTypeMap.put(qualifiedColumnName, type.name)
                                    } else if (it.elementType instanceof PrimitiveType) {
                                        PrimitiveType type = (PrimitiveType) it.elementType
                                        columnDataTypeMap.put(qualifiedColumnName, type.toBoxedType().name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Map<String, String> getColumnDataTypeMap() {
        return columnDataTypeMap
    }

    void scan(SourceCodeExtract sourceCodeExtract) {
        //todo: probably a better way but for now just look for javax.persistence import
        for (ImportDeclaration importDeclaration : compilationUnit.imports) {
            if (importDeclaration.toStringWithoutComments().contains("javax.persistence")) {
                compilationUnit.accept(this, JavaParserFacade.get(typeSolver))
                break
            }
        }
    }

}
