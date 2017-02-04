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
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import org.mapdb.DB

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryColumnJoinExtractor extends NomadExtractor {

    private Map<String, String> columnJoinMap
    private Map<String, String> fieldMappedByMap
    private Map<String, String> fieldMappedByTypeMap
    private Map<String, String> joinTableMappedByTypeMap
    private Map<String, List<String>> joinTableMap
    private Map<String, List<String>> inverseJoinTableMap
    private Map<String, String> joinTableTypeMap
    private Map<String, String> embeddedJoinTableTypeMap

    QueryColumnJoinExtractor(CompilationUnit compilationUnit, File sourceFile, TypeSolver typeSolver, DB db) {
        super(compilationUnit, sourceFile, typeSolver, db)
        columnJoinMap = new HashMap<>()
        fieldMappedByMap = new HashMap<>()
        fieldMappedByTypeMap = new HashMap<>()
        joinTableMappedByTypeMap = new HashMap<>()
        joinTableMap = new HashMap<>()
        inverseJoinTableMap = new HashMap<>()
        joinTableTypeMap = new HashMap<>()
        embeddedJoinTableTypeMap = new HashMap<>()
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
            columnJoinMap = new ObjectMapper().readValue(storedString,
                    new TypeReference<HashMap<String, String>>() {})
            //skipScan = true
            //todo: cache
        }
    }

    @Override
    void saveCache() {
        def fileHashMap = getStorableStringStringMap("file_hash_map")
        def writer = new StringWriter()
        new ObjectMapper().writeValue(writer, columnJoinMap)
        fileHashMap.put(getFileChecksum(), writer.toString())
    }

    @Override
    void visit(final ClassOrInterfaceDeclaration n, final JavaParserFacade javaParser) {
        n.members.childrenNodes.each {
            if (it instanceof FieldDeclaration) {
                for (def annotation : it.annotations) {
                    if (annotation.name.name == "JoinTable") {
                        if (annotation instanceof NormalAnnotationExpr) {

                            def sqlTableName = null
                            def joinColumnList = new ArrayList<>()
                            def inverseJoinColumnList = new ArrayList<>()
                            for (def pair : annotation.pairs) {
                                if (pair.name == "name") {
                                    sqlTableName = pair.value.toStringWithoutComments().toLowerCase().replace("\"", "")
                                } else if (pair.name == "joinColumns") {
                                    if (pair.value instanceof NormalAnnotationExpr) {
                                        ((NormalAnnotationExpr) pair.value).pairs.each {
                                            if (it.name == "name") {
                                                joinColumnList.add(it.value.toStringWithoutComments().toLowerCase().replace("\"", ""))
                                            }
                                        }
                                    }
                                } else if (pair.name == "inverseJoinColumns") {
                                    if (pair.value instanceof NormalAnnotationExpr) {
                                        ((NormalAnnotationExpr) pair.value).pairs.each {
                                            if (it.name == "name") {
                                                inverseJoinColumnList.add(it.value.toStringWithoutComments().toLowerCase().replace("\"", ""))
                                            }
                                        }
                                    }
                                }
                            }

                            if (!joinColumnList.isEmpty()) {
                                joinTableMap.put(sqlTableName, joinColumnList)
                                joinTableTypeMap.put(it.variables.get(0).id.name.toLowerCase(), sqlTableName)
                            }
                            if (!inverseJoinColumnList.isEmpty()) {
                                inverseJoinTableMap.put(sqlTableName, inverseJoinColumnList)
                                joinTableTypeMap.put(it.variables.get(0).id.name.toLowerCase(), sqlTableName)
                            }

                            if (it.elementType instanceof ClassOrInterfaceType) {
                                def classType = (ClassOrInterfaceType) it.elementType
                                if (classType.typeArguments.isPresent()) {
                                    def typeArgs = classType.typeArguments.get()
                                    if (typeArgs.size() > 1) {
                                        def joinType = null
                                        typeArgs.each {
                                            if (it instanceof ClassOrInterfaceType) {
                                                if (!isKnownDataType(it.name)) {
                                                    joinType = it.name
                                                }
                                            }
                                        }

                                        if (joinType == null) {
                                            joinTableMappedByTypeMap.put(it.variables.get(0).id.name.toLowerCase(), typeArgs.get(0).toStringWithoutComments())
                                        } else {
                                            joinTableMappedByTypeMap.put(it.variables.get(0).id.name.toLowerCase(), joinType)
                                        }
                                    } else {
                                        joinTableMappedByTypeMap.put(it.variables.get(0).id.name.toLowerCase(), typeArgs.get(0).toStringWithoutComments())
                                    }
                                }
                            }
                        }
                    } else if (annotation.name.name == "JoinColumn") {
                        if (annotation instanceof NormalAnnotationExpr) {
                            for (def pair : annotation.pairs) {
                                if (pair.name == "name") {
                                    def qualifiedColumnName = pair.value.toStringWithoutComments().toLowerCase().replace("\"", "")
                                    columnJoinMap.put(it.variables.get(0).id.name.toLowerCase(), qualifiedColumnName)
                                }
                            }
                        }
                    } else if (annotation.name.name == "OneToMany") {
                        if (annotation instanceof NormalAnnotationExpr) {
                            for (def pair : annotation.pairs) {
                                if (pair.name == "mappedBy") {
                                    def qualifiedColumnName = pair.value.toStringWithoutComments().toLowerCase().replace("\"", "")
                                    fieldMappedByMap.put(it.variables.get(0).id.name.toLowerCase(), qualifiedColumnName)
                                }
                            }
                        }

                        if (it.elementType instanceof ClassOrInterfaceType) {
                            ClassOrInterfaceType type = (ClassOrInterfaceType) it.elementType
                            if (type.typeArguments.isPresent()) {
                                fieldMappedByTypeMap.put(it.variables.get(0).id.name.toLowerCase(), ((ClassOrInterfaceType) type.typeArguments.get().get(0)).name)
                            }
                        }
                    } else if (annotation.name.name == "ManyToOne") {
                        if (it.elementType instanceof ClassOrInterfaceType) {
                            ClassOrInterfaceType type = (ClassOrInterfaceType) it.elementType
                            fieldMappedByTypeMap.put(it.variables.get(0).id.name.toLowerCase(), type.name)
                        }
                    } else if (annotation.name.name == "Embedded") {
                        if (it.elementType instanceof ClassOrInterfaceType) {
                            ClassOrInterfaceType type = (ClassOrInterfaceType) it.elementType
                            embeddedJoinTableTypeMap.put(it.variables.get(0).id.name.toLowerCase(), type.name)
                        }
                    }
                }
            }
        }
    }

    //todo: get rid of this
    static boolean isKnownDataType(String dataType) {
        switch (dataType) {
            case "Boolean":
            case "Long":
            case "Integer":
            case "Date":
            case "String":
                return true
            default: return false
        }
    }

    Map<String, String> getColumnJoinMap() {
        return columnJoinMap //todo: these columns should also be in column alias extractor
    }

    Map<String, String> getFieldMappedByMap() {
        return fieldMappedByMap
    }

    Map<String, String> getFieldMappedByTypeMap() {
        return fieldMappedByTypeMap
    }

    Map<String, String> getJoinTableMappedByTypeMap() {
        return joinTableMappedByTypeMap
    }

    Map<String, List<String>> getJoinTableMap() {
        return joinTableMap
    }

    Map<String, List<String>> getInverseJoinTableMap() {
        return inverseJoinTableMap
    }

    Map<String, String> getJoinTableTypeMap() {
        return joinTableTypeMap
    }

    Map<String, String> getEmbeddedJoinTableTypeMap() {
        return embeddedJoinTableTypeMap
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
