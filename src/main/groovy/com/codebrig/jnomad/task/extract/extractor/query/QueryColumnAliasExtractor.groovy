package com.codebrig.jnomad.task.extract.extractor.query

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.extract.NomadExtractor
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.imports.ImportDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import org.mapdb.DB

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryColumnAliasExtractor extends NomadExtractor {

    private Map<String, String> columnNameAliasMap = new HashMap<>()
    private String extractorName = getClass().name
    private String idName

    QueryColumnAliasExtractor(CompilationUnit compilationUnit, File sourceFile, TypeSolver typeSolver, DB db) {
        super(compilationUnit, sourceFile, typeSolver, db)
    }

    @Override
    String getName() {
        return extractorName
    }

    @Override
    void loadCache() {
        def fileHashMap = getStorableStringStringMap("file_hash_map")
        if (fileHashMap.containsKey(getFileChecksum())) {
            //already scanned this file
            String storedString = fileHashMap.get(getFileChecksum())
            columnNameAliasMap = new ObjectMapper().readValue(storedString,
                    new TypeReference<HashMap<String, String>>() {})
            //skipScan = true
            //todo: cache
        }
    }

    @Override
    void saveCache() {
        def fileHashMap = getStorableStringStringMap("file_hash_map")
        def writer = new StringWriter()
        new ObjectMapper().writeValue(writer, columnNameAliasMap)
        fileHashMap.put(getFileChecksum(), writer.toString())
    }

    @Override
    void visit(final ClassOrInterfaceDeclaration n, final JavaParserFacade javaParser) {
        n.members.childrenNodes.each {
            if (it instanceof FieldDeclaration) {
                for (def annotation : it.annotations) {
                    if (annotation.name.name == "Column") {
                        if (annotation instanceof NormalAnnotationExpr) {
                            for (def pair : annotation.pairs) {
                                if (pair.name == "name") {
                                    def alias = pair.value.toStringWithoutComments().toLowerCase().replace("\"", "")
                                    def varName = it.variables.get(0).id.name.toLowerCase()
                                    columnNameAliasMap.put(varName, alias)
                                    columnNameAliasMap.put(alias, alias)
                                }
                            }
                        }
                    } else if (annotation.name.name == "Id") {
                        if (annotation instanceof MarkerAnnotationExpr) {
                            idName = it.variables.get(0).id.name.toLowerCase()
                        }
                    }
                }
            }
        }
    }

    Map<String, String> getColumnNameAliasMap() {
        return columnNameAliasMap
    }

    String getIdName() {
        return idName
    }

    void scan(SourceCodeExtract sourceCodeExtract, CompilationUnit compilationUnit) {
        //todo: probably a better way but for now just look for javax.persistence import
        for (ImportDeclaration importDeclaration : compilationUnit.imports) {
            if (importDeclaration.toStringWithoutComments().contains("javax.persistence")) {
                compilationUnit.accept(this, JavaParserFacade.get(typeSolver))

                if (idName != null) {
                    def sqlColumnName = columnNameAliasMap.get(idName)
                    if (sqlColumnName != null) {
                        idName = sqlColumnName
                    }
                    columnNameAliasMap.put("id", idName)
                }
                break
            }
        }
    }

}
