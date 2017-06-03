package com.codebrig.jnomad.task.extract.extractor.query

import com.codebrig.jnomad.model.SourceCodeExtract
import com.codebrig.jnomad.task.extract.NomadExtractor
import com.codebrig.jnomad.task.resolve.QueryExpressionResolver
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.javaparser.Position
import com.github.javaparser.Range
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.imports.ImportDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.javaparsermodel.UnsolvedSymbolException
import com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.github.javaparser.symbolsolver.model.typesystem.ReferenceType
import com.github.javaparser.symbolsolver.model.typesystem.Type
import org.mapdb.DB

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryLiteralExtractor extends NomadExtractor {

    public static boolean isDisabled = false //todo: better
    private TypeDeclaration queryType
    private foundQuery
    private foundDynamicQuery
    private possibleQueryList = new ArrayList<String>()
    private possibleDynamicQueryList = new ArrayList<String>()
    private extractorName = getClass().name
    private Map<String, int[]> queryCallRangeMap = new HashMap<>()
    private Map<String, String> originalExpressionMap = new HashMap<>()

    QueryLiteralExtractor(CompilationUnit compilationUnit, File sourceFile, TypeSolver typeSolver, DB cache) {
        super(compilationUnit, sourceFile, typeSolver, cache)
        this.queryType = typeSolver.solveType("javax.persistence.Query")
    }

    @Override
    void visit(MethodCallExpr methodCallExpr, JavaParserFacade javaParserFacade) {
        super.visit(methodCallExpr, javaParserFacade)
        JavaParserFacade.clearInstances()

        boolean solveMethodType = true
        try {
            if (methodCallExpr.getScope().isPresent()) {
                def methodScopeVariable = javaParserFacade.getType(methodCallExpr.getScope().get())
                if (isConcreteTypeQuery(methodScopeVariable)) {
                    solveMethodType = false //method scope is Query; no need to solve type
                }
            }

            //todo: only look at query methods
            if (solveMethodType && javaParserFacade.getType(methodCallExpr).referenceType) {
                def type = javaParserFacade.getType(methodCallExpr)
                if (isConcreteTypeQuery(type)) {
                    def methodRef = javaParserFacade.solve(methodCallExpr)
                    if (methodRef.solved) {
                        def queryExtractor = new QueryExpressionResolver(typeSolver, javaParserFacade)
                        queryExtractor.findPossibleQueries(methodCallExpr)

                        def possibleQueries = queryExtractor.getPossibleQueries()
                        if (!possibleQueries.isEmpty()) {
                            println "Found static queries: " + Arrays.toString(possibleQueries) + "\n\tSource code file: " + getClassName()
                            possibleQueries.each {
                                def query = it.value.toLowerCase()
                                possibleQueryList.add(query)
                                queryCallRangeMap.put(query, toIntArray(methodCallExpr.range))
                                originalExpressionMap.put(query, methodCallExpr.toString())
                            }
                            foundQuery = true
                        }
                        def possibleDynamicQueries = queryExtractor.getPossibleDynamicQueries()
                        if (!possibleDynamicQueries.isEmpty()) {
                            println "Found dynamic queries: " + Arrays.toString(possibleDynamicQueries) + "\n\tSource code file: " + getClassName()
                            possibleDynamicQueries.each {
                                def query = it.toStringWithoutComments().toLowerCase()
                                possibleDynamicQueryList.add(query)
                                queryCallRangeMap.put(query, toIntArray(methodCallExpr.range))
                                originalExpressionMap.put(query, methodCallExpr.toString())
                            }
                            foundDynamicQuery = true
                        }
                    } else {
                        println(" ???")
                    }
                }
            }
        } catch (UnsolvedSymbolException ex) {
        } catch (Exception ex) {
            //todo: aggregate and log errors
            //println ex.printStackTrace()
            //println className + "; error - " + ex.getMessage()
        }
    }

    boolean isConcreteTypeQuery(Type type) {
        def ancestorList = new ArrayList<ReferenceType>(type.asReferenceType().allAncestors)
        for (def ancestor : ancestorList) {
            def selfHasType = type.asReferenceType().typeDeclaration.qualifiedName == queryType.qualifiedName
            def ancestorHasType = ancestor.typeDeclaration.qualifiedName == queryType.qualifiedName

            if (selfHasType || ancestorHasType) {
                return true
            }
        }
        return false
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
            String storableStr = fileHashMap.get(getFileChecksum())
            Map<String, String> underMap = new ObjectMapper().readValue(storableStr,
                    new TypeReference<HashMap<String, String>>() {})

            possibleQueryList = new ObjectMapper().readValue(underMap.get("possibleQueryList"),
                    new TypeReference<List<String>>() {})
            possibleDynamicQueryList = new ObjectMapper().readValue(underMap.get("possibleDynamicQueryList"),
                    new TypeReference<List<String>>() {})
            queryCallRangeMap = new ObjectMapper().readValue(underMap.get("queryCallRangeMap"),
                    new TypeReference<Map<String, int[]>>() {})
            originalExpressionMap = new ObjectMapper().readValue(underMap.get("originalExpressionMap"),
                    new TypeReference<Map<String, String>>() {})

            foundQuery = !possibleQueryList.isEmpty()
            foundDynamicQuery = !possibleDynamicQueryList.isEmpty()
            skipScan = true

            if (!possibleQueryList.isEmpty()) {
                println "Found cached static queries: " + Arrays.toString(possibleQueryList) + "\n\tSource code file: " + getClassName()
            }
            if (!possibleDynamicQueryList.isEmpty()) {
                println "Found cached dynamic queries: " + Arrays.toString(possibleDynamicQueryList) + "\n\tSource code file: " + getClassName()
            }
        }
    }

    @Override
    void saveCache() {
        def fileHashMap = getStorableStringStringMap("file_hash_map")

        def map = new HashMap<String, String>()

        def writer = new StringWriter()
        new ObjectMapper().writeValue(writer, possibleQueryList)
        map.put("possibleQueryList", writer.toString())

        writer = new StringWriter()
        new ObjectMapper().writeValue(writer, possibleDynamicQueryList)
        map.put("possibleDynamicQueryList", writer.toString())

        writer = new StringWriter()
        new ObjectMapper().writeValue(writer, queryCallRangeMap)
        map.put("queryCallRangeMap", writer.toString())

        writer = new StringWriter()
        new ObjectMapper().writeValue(writer, originalExpressionMap)
        map.put("originalExpressionMap", writer.toString())

        writer = new StringWriter()
        new ObjectMapper().writeValue(writer, map)
        fileHashMap.put(getFileChecksum(), writer.toString())
    }

    @Override
    void scan(SourceCodeExtract sourceCodeExtract, CompilationUnit compilationUnit) {
        if (isDisabled) {
            return
        }

        //todo: probably a better way but for now just look for javax.persistence import
        for (ImportDeclaration importDeclaration : compilationUnit.imports) {
            if (importDeclaration.toStringWithoutComments().contains("javax.persistence")) {
                compilationUnit.accept(this, JavaParserFacade.get(typeSolver))
                break
            }
        }
    }

    boolean getQueryFound() {
        return foundQuery || foundDynamicQuery
    }

    def getPossibleQueryList() {
        return possibleQueryList
    }

    def getPossibleDynamicQueryList() {
        return possibleDynamicQueryList
    }

    public Range getQueryCallRange(String query) {
        return toRange(queryCallRangeMap.get(Objects.requireNonNull(query).toLowerCase()))
    }

    def getAllPossibleQueryList() {
        def returnList = new ArrayList<String>()
        possibleQueryList.each {
            returnList.add(it)
        }
        possibleDynamicQueryList.each {
            returnList.add(it)
        }
        return returnList
    }

    String getQueryOriginalExpression(String query) {
        return originalExpressionMap.get(query)
    }

    private static int[] toIntArray(Range range) {
        return [
                range.begin.line, range.begin.column,
                range.end.line, range.end.column
        ]
    }

    private static Range toRange(int[] intArr) {
        return new Range(
                new Position(intArr[0], intArr[1]),
                new Position(intArr[2], intArr[3])
        )
    }

}
