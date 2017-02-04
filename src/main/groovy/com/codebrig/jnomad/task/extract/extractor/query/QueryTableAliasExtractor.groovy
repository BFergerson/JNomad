package com.codebrig.jnomad.task.extract.extractor.query

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.extract.NomadExtractor
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.imports.ImportDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import org.mapdb.DB

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryTableAliasExtractor extends NomadExtractor {

    private Map<String, String> tableNameAliasMap = new HashMap<>()
    private boolean embeddableTable

    QueryTableAliasExtractor(CompilationUnit compilationUnit, File sourceFile, TypeSolver typeSolver, DB db) {
        super(compilationUnit, sourceFile, typeSolver, db)
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
            tableNameAliasMap = new ObjectMapper().readValue(storedString,
                    new TypeReference<HashMap<String, String>>() {})
            //skipScan = true
            //todo: cache
        }
    }

    @Override
    void saveCache() {
        def fileHashMap = getStorableStringStringMap("file_hash_map")
        def writer = new StringWriter()
        new ObjectMapper().writeValue(writer, tableNameAliasMap)
        fileHashMap.put(getFileChecksum(), writer.toString())
    }

    @Override
    void visit(final ClassOrInterfaceDeclaration n, final JavaParserFacade javaParser) {
        n.annotations.each {
            if (it.name.toStringWithoutComments().contains("Table")) {
                if (it instanceof NormalAnnotationExpr) {
                    for (def pair : it.pairs) {
                        if (pair.name == "name") {
                            def alias = pair.value.toStringWithoutComments().toLowerCase().replace("\"", "")
                            tableNameAliasMap.put(className.toLowerCase(), alias)
                            tableNameAliasMap.put(alias, alias)
                        }
                    }
                }
            } else if (it.name.toStringWithoutComments().contains("Embeddable")) {
                embeddableTable = true
            }
        }
    }

    Map<String, String> getTableNameAliasMap() {
        return tableNameAliasMap
    }

    boolean getEmbeddableTable() {
        return embeddableTable
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
