package com.codebrig.jnomad.task.resolve

import com.codebrig.jnomad.utils.CodeLocator
import com.codebrig.jnomad.utils.StringLiteralConcatenator
import com.github.javaparser.ast.expr.*
import com.github.javaparser.symbolsolver.javaparser.Navigator
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration
import com.github.javaparser.symbolsolver.model.declarations.MethodDeclaration
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver

/**
 * This class is fed Java source code files that utilize javax.persistence.Query;
 * It will resolve simple variables (String/String(Builder/Buffer)/Primitive) and methods
 * to extract queries that could possibly be executed by the source code file.
 *
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class QueryExpressionResolver {

    private TypeSolver typeSolver
    private JavaParserFacade javaParserFacade
    private ArrayList<StringLiteralExpr> possibleQueries = new ArrayList<>()
    private ArrayList<Expression> possibleDynamicQueries = new ArrayList<>()

    QueryExpressionResolver(TypeSolver typeSolver, JavaParserFacade javaParserFacade) {
        this.typeSolver = typeSolver
        this.javaParserFacade = javaParserFacade
    }

    void findPossibleQueries(MethodCallExpr queryMethodCall) {
        def args = queryMethodCall.args
        def stringExpression = args.get(0)

        if (stringExpression instanceof StringLiteralExpr) {
            //query with string literal
            possibleQueries.add(stringExpression)
        } else if (stringExpression instanceof MethodCallExpr) {
            //query with method call
            def ref = javaParserFacade.solve(stringExpression)
            if (ref.solved) {
                def declaration = (MethodDeclaration) ref.correspondingDeclaration
                def stringBufferType = typeSolver.solveType("java.lang.StringBuffer")
                def stringBuilderType = typeSolver.solveType("java.lang.StringBuilder")
                if (declaration.declaringType().qualifiedName == stringBufferType.qualifiedName
                        || declaration.declaringType().qualifiedName == stringBuilderType.qualifiedName) {
                    //using string buffer/builder
                    def strNameExpr = (NameExpr) stringExpression.childrenNodes.get(0)
                    ref = javaParserFacade.solve(strNameExpr)
                    if (ref.solved) {
                        def variableResolver = new VariableDomainResolver(typeSolver, javaParserFacade)
                        variableResolver.solveVariable(ref, strNameExpr.range, true)
                        possibleQueries.addAll(variableResolver.possibleQueryList)
                        possibleDynamicQueries.addAll(variableResolver.possibleDynamicQueryList)
                    }
                } else {
                    for (def inner : Navigator.getMethodCalls(stringExpression)) {
                        //todo: how does below work and what does it do
                        SymbolReference test2 = javaParserFacade.solve(inner)
                        JavaParserMethodDeclaration methodDeclaration = (JavaParserMethodDeclaration) test2.correspondingDeclaration
                        StringLiteralExpr stringLiteral = StringLiteralConcatenator.concatStringLiteral(
                                stringExpression, methodDeclaration.getWrappedNode(), javaParserFacade)
                        possibleQueries.add(stringLiteral)
                    }
                }
            }
        } else if (stringExpression instanceof BinaryExpr) {
            //query with string concatenation
            handleStringConcatenation(stringExpression)
        } else {
            //query with string variable
            def ref = javaParserFacade.solve(stringExpression)
            if (ref.isSolved()) {
                def variableResolver = new VariableDomainResolver(typeSolver, javaParserFacade)
                variableResolver.solveVariable(ref, stringExpression.range, false)
                possibleQueries.addAll(variableResolver.possibleQueryList)
                possibleDynamicQueries.addAll(variableResolver.possibleDynamicQueryList)
            }
        }
    }

    private void handleStringConcatenation(BinaryExpr stringExpression) {
        //check if complete string literal or more complex
        if (StringLiteralConcatenator.isFullyStringLiteral(stringExpression)) {
            possibleQueries.add(StringLiteralConcatenator.handleFullyStringLiteral(stringExpression))
        } else {
            //may involve variables/methods
            for (def inner : Navigator.getMethodCalls(stringExpression)) {
                SymbolReference test2 = javaParserFacade.solve(inner)
                if (test2.correspondingDeclaration instanceof JavaParserMethodDeclaration) {
                    JavaParserMethodDeclaration methodDeclaration = (JavaParserMethodDeclaration) test2.correspondingDeclaration

                    boolean isConcreteClass = !Navigator.findClassOrInterfaceDeclarationExpression(methodDeclaration.getWrappedNode()).isInterface()
                    if (isConcreteClass) {
                        StringLiteralExpr stringLiteral = StringLiteralConcatenator.concatStringLiteral(
                                stringExpression, methodDeclaration.getWrappedNode(), javaParserFacade)
                        possibleQueries.add(stringLiteral)
                    } else {
                        for (def method : CodeLocator.locateMethodImplementations(typeSolver, methodDeclaration)) {
                            StringLiteralExpr stringLiteral = StringLiteralConcatenator.concatStringLiteral(
                                    stringExpression, method, javaParserFacade)
                            possibleQueries.add(stringLiteral)
                        }
                    }
                } else {
                    println "Missing required source code (${test2.correspondingDeclaration}) to solve: " + stringExpression
                    possibleDynamicQueries.add(stringExpression)
                }
            }
        }
    }

    ArrayList<StringLiteralExpr> getPossibleQueries() {
        return possibleQueries
    }

    ArrayList<Expression> getPossibleDynamicQueries() {
        return possibleDynamicQueries
    }

    ArrayList<Expression> getAllPossibleQueries() {
        def returnList = new ArrayList<Expression>()
        possibleQueries.each {
            returnList.add(it)
        }
        possibleDynamicQueries.each {
            returnList.add(it)
        }
        return returnList
    }

}
