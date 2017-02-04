package com.codebrig.jnomad.task.resolve

import com.codebrig.jnomad.model.DynamicQuery
import com.codebrig.jnomad.model.UserDataKeys
import com.codebrig.jnomad.utils.CodeLocator
import com.codebrig.jnomad.utils.StringLiteralConcatenator
import com.github.javaparser.Range
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.body.VariableDeclaratorId
import com.github.javaparser.ast.expr.*
import com.github.javaparser.symbolsolver.javaparser.Navigator
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.javaparsermodel.UnsolvedSymbolException
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserSymbolDeclaration
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class VariableDomainResolver {

    private TypeSolver typeSolver
    private JavaParserFacade javaParserFacade
    private possibleQueryList = new ArrayList<StringLiteralExpr>()
    private possibleDynamicQueryList = new ArrayList<Expression>()

    VariableDomainResolver(TypeSolver typeSolver, JavaParserFacade javaParserFacade) {
        this.typeSolver = typeSolver
        this.javaParserFacade = javaParserFacade
    }

    ResolvedVariable solveVariable(SymbolReference ref, Range createQueryCallRange, boolean stringBuilder) {
        boolean dynamicVariable = false

        def variableLocation
        VariableDeclarator variableDeceleration
        Node parent
        List<StringLiteralExpr> stringLiteralList = new ArrayList<>()
        def queryStringDeclaration = ref.correspondingDeclaration
        if (queryStringDeclaration.field) {
            JavaParserFieldDeclaration fieldDeclaration = (JavaParserFieldDeclaration) queryStringDeclaration
            variableDeceleration = (VariableDeclarator) fieldDeclaration.wrappedNode.getVariables().get(0)

            //todo: in CampaignSynchronizer search for hql field and notice there is no assign; determine as dynamic
            //look everywhere
            parent = Navigator.demandCompilationUnit(variableDeceleration)
            variableLocation = Navigator.findClassOrInterfaceDeclarationExpression(variableDeceleration).getName()
        } else {
            JavaParserSymbolDeclaration symbolDeclaration = (JavaParserSymbolDeclaration) queryStringDeclaration
            variableDeceleration = (VariableDeclarator) symbolDeclaration.wrappedNode

            //look everywhere in parent method
            //todo: prior to query use
            def methodDeclaration = parent = Navigator.getParentMethodDeclerationExpression(variableDeceleration)
            variableLocation = Navigator.findClassOrInterfaceDeclarationExpression(
                    variableDeceleration).getName() + "." + methodDeclaration.getName() + "()"
        }

        println "Solving source code variable: " + variableLocation + ":" + ref.correspondingDeclaration.getName()

        boolean combineStringLiterals = true
        //loop any method calls in query string declaration
        def methodCalls = Navigator.getMethodCalls(variableDeceleration)
        if (methodCalls.empty && variableDeceleration.init.present) {
            //no method calls in query string deceleration; must be string literal (with or without concatenation)
            def initExpression = variableDeceleration.init.get()
            if (StringLiteralConcatenator.isFullyStringLiteral(initExpression)) {
                if (initExpression instanceof BinaryExpr) {
                    stringLiteralList.add(StringLiteralConcatenator.handleFullyStringLiteral(initExpression))
                } else if (initExpression instanceof StringLiteralExpr) {
                    stringLiteralList.add(initExpression)
                }
            } else if (initExpression instanceof ConditionalExpr) {
                //todo: could call it dynamic and call it quits?
                //string literal with conditional
                println "todo solve this: " + initExpression
                //todo: solve this; will need to be more robust though as methods initializing the variable
                //can also use conditionals and conditionals can be stacked
            }
        } else {
            for (def inner : methodCalls) {
                //can we solve method call scope?
                if (inner.getScope().isPresent()) {
                    SymbolReference symbolReference
                    try {
                        symbolReference = javaParserFacade.solve(inner.getScope().get())
                        if (!symbolReference.solved) {
                            //can't solve; dynamic
                            dynamicVariable = true
                            continue
                        }
                    } catch (Exception ex) {
                        if (ex.getCause() != null && ex.getCause() instanceof UnsolvedSymbolException) {
                            //couldn't solve method call scope; determining as dynamic
                            dynamicVariable = true
                            continue
                        } else {
                            throw new RuntimeException(ex)
                        }
                    }

                    def varSolver = new VariableDomainResolver(typeSolver, javaParserFacade)
                    def resolvedVariable = varSolver.solveVariable(symbolReference, inner.getScope().get().range, false)
                    if (resolvedVariable.dynamicVariable) {
                        dynamicVariable = true
                    }
                }

                //can we solve method call?
                SymbolReference symbolReference
                try {
                    symbolReference = javaParserFacade.solve(inner)
                    if (!symbolReference.solved) {
                        //can't solve; dynamic
                        dynamicVariable = true
                        continue
                    }
                } catch (Exception ex) {
                    if ((ex.getCause() != null && ex.getCause() instanceof UnsolvedSymbolException) || UnsupportedOperationException) {
                        //couldn't solve method call; determining as dynamic
                        dynamicVariable = true
                        continue
                    } else {
                        throw new RuntimeException(ex)
                    }
                }


                JavaParserMethodDeclaration methodDeclaration = (JavaParserMethodDeclaration) symbolReference.correspondingDeclaration

                //loop expressions in variable deceleration
                for (def childNode : variableDeceleration.childrenNodes) {
                    if (childNode instanceof BinaryExpr) {
                        //variable declared by binary expression (containing method call)
                        //todo: and append/add binary
                        boolean isConcreteClass = !Navigator.findClassOrInterfaceDeclarationExpression(methodDeclaration.wrappedNode).interface
                        if (isConcreteClass) {
                            StringLiteralExpr stringLiteral = StringLiteralConcatenator.concatStringLiteral(
                                    childNode, methodDeclaration.wrappedNode, javaParserFacade)
                            if (stringLiteral.value.empty) {
                                dynamicVariable = true
                            } else {
                                stringLiteralList.add(stringLiteral)
                            }
                        } else {
                            for (def method : CodeLocator.locateMethodImplementations(typeSolver, methodDeclaration)) {
                                StringLiteralExpr stringLiteral = StringLiteralConcatenator.concatStringLiteral(
                                        childNode, method, javaParserFacade)
                                if (stringLiteral.value.empty) {
                                    dynamicVariable = true
                                } else {
                                    stringLiteralList.add(stringLiteral)
                                    combineStringLiterals = false
                                }
                            }
                        }
                    } else if (childNode instanceof MethodCallExpr) {
                        //variable declared by method call alone
                        boolean isConcreteClass = !Navigator.findClassOrInterfaceDeclarationExpression(methodDeclaration.wrappedNode).interface
                        if (isConcreteClass) {
                            StringLiteralExpr stringLiteral = StringLiteralConcatenator.determineStringLiteral(
                                    methodDeclaration.wrappedNode, javaParserFacade)
                            if (stringLiteral.value.empty) {
                                dynamicVariable = true
                            } else {
                                stringLiteralList.add(stringLiteral)
                            }
                        } else {
                            for (def method : CodeLocator.locateMethodImplementations(typeSolver, methodDeclaration)) {
                                StringLiteralExpr stringLiteral = StringLiteralConcatenator.determineStringLiteral(
                                        method, javaParserFacade)
                                if (stringLiteral.value.empty) {
                                    dynamicVariable = true
                                } else {
                                    stringLiteralList.add(stringLiteral)
                                    combineStringLiterals = false
                                }
                            }
                        }
                    }
                }
            }
        }

        if (stringBuilder) {
            solveStringBuilder(createQueryCallRange, parent, (JavaParserSymbolDeclaration) queryStringDeclaration)
        } else {
            //at this point we got query variable declaration
            //now need to take into affect any modifications it has before query is executed

            //todo: difference between assign and +=
            //todo: only assigns before query call
            AssignExpr expr
            def exprList = new ArrayList<AssignExpr>()
            while ((expr = Navigator.findAssignExpressionToTarget(parent, queryStringDeclaration, exprList)) != null) {
                Expression value = expr.value
                if (value instanceof StringLiteralExpr) {
                    stringLiteralList.add(value)
                } else if (value instanceof MethodCallExpr) {
                    SymbolReference symbolReference = javaParserFacade.solve(value)
                    JavaParserMethodDeclaration methodDeclaration = (JavaParserMethodDeclaration) symbolReference.correspondingDeclaration
                    StringLiteralConcatenator.determineStringLiteral(methodDeclaration.wrappedNode, javaParserFacade)
                }
            }
            if (!stringLiteralList.isEmpty()) {
                if (combineStringLiterals) {
                    possibleQueryList.add(StringLiteralConcatenator.simpleConcatenate(stringLiteralList))
                } else {
                    possibleQueryList.addAll(stringLiteralList)
                }
            }
        }

        def resolvedVariable = new ResolvedVariable()
        resolvedVariable.dynamicVariable = dynamicVariable
        return resolvedVariable
    }

    private void solveStringBuilder(Range createQueryCallRange, Node parent, JavaParserSymbolDeclaration queryStringDeceleration) {
        def innerPossibleSet = new HashSet<List<StringLiteralExpr>>()
        innerPossibleSet.add(new ArrayList<StringLiteralExpr>())

        //solve initial value
        VariableDeclarator variableDeclarator = (VariableDeclarator) queryStringDeceleration.wrappedNode
        if (variableDeclarator.init.present) {
            ObjectCreationExpr creationExpr = (ObjectCreationExpr) variableDeclarator.init.get()
            for (Expression expression : creationExpr.args) {
                if (expression instanceof StringLiteralExpr) {
                    //initial value by string literal
                    for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                        innerPossibleList.add(expression)
                    }
                } else if (expression instanceof MethodCallExpr) {
                    //initial value by method call
                    SymbolReference symbolReference = javaParserFacade.solve(expression)
                    JavaParserMethodDeclaration methodDeclaration = (JavaParserMethodDeclaration) symbolReference.correspondingDeclaration
                    boolean isConcreteClass = !Navigator.findClassOrInterfaceDeclarationExpression(methodDeclaration.wrappedNode).interface
                    if (isConcreteClass) {
                        StringLiteralExpr stringLiteral = StringLiteralConcatenator.determineStringLiteral(methodDeclaration.wrappedNode, javaParserFacade)
                        for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                            innerPossibleList.add(stringLiteral)
                        }
                    } else {
                        def alternatePossibleSet = new HashSet<List<StringLiteralExpr>>()
                        for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                            for (def method : CodeLocator.locateMethodImplementations(typeSolver, methodDeclaration)) {
                                StringLiteralExpr stringLiteral = StringLiteralConcatenator.determineStringLiteral(method, javaParserFacade)

                                List<StringLiteralExpr> alternateStringLiteralList = new ArrayList<>()
                                alternateStringLiteralList.addAll(innerPossibleList)
                                alternateStringLiteralList.add(stringLiteral)

                                alternatePossibleSet.add(alternateStringLiteralList)
                            }
                        }
                        innerPossibleSet = alternatePossibleSet
                    }
                }
            }
        }

        def list = Navigator.getStringBuilderInitAndAppendMethodCalls(parent, queryStringDeceleration.name)

        //find init/assign
        def initExpr = null
        list.reverseEach {
            if (initExpr == null && it.range.isBefore(createQueryCallRange.begin)) {
                if (it instanceof VariableDeclarator) {
                    initExpr = it
                } else if (it instanceof AssignExpr) {
                    initExpr = it
                }
            }
        }

        //aggregate appends
        for (def inner : list) {
            boolean afterVariableInit
            if (initExpr != null) {
                afterVariableInit = inner.range.isAfter(initExpr.end)
            } else {
                afterVariableInit = inner.range.isAfter(variableDeclarator.end)
            }
            boolean beforeQueryCall = inner.range.isBefore(createQueryCallRange.begin)
            if (!afterVariableInit || !beforeQueryCall) {
                continue //not this string builder's scope
            }

            if (inner instanceof StringLiteralExpr) {
                for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                    innerPossibleList.add(inner)
                }
            } else if (inner instanceof MethodCallExpr) {
                SymbolReference symbolReference
                try {
                    symbolReference = javaParserFacade.solve(inner)
                } catch (UnsolvedSymbolException ex) {
                    //couldn't solve method call; determining as dynamic
                    for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                        StringLiteralExpr literalExpr = new StringLiteralExpr(inner.toStringWithoutComments())
                        literalExpr.setUserData(UserDataKeys.DYNAMIC_QUERY_KEY, new DynamicQuery())
                        innerPossibleList.add(literalExpr)
                    }
                    continue
                }

                //can we solve arguments? (if it has arguments)
                boolean isDynamic = false
                for (Expression expr : inner.args) {
                    if (expr instanceof FieldAccessExpr) {
                        Expression scope = expr.scope
                        if (scope != null && scope instanceof NameExpr) {
                            Node declarator = Navigator.forceDemandVariableDeclaration(expr, scope.getName())
                            if (declarator instanceof VariableDeclaratorId) {
                                //can't look up; enemy's gate is down; dynamic
                                isDynamic = true
                                for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                                    StringLiteralExpr literalExpr = new StringLiteralExpr(inner.toStringWithoutComments())
                                    literalExpr.setUserData(UserDataKeys.DYNAMIC_QUERY_KEY, new DynamicQuery())
                                    innerPossibleList.add(literalExpr)
                                }
                                break
                            }
                        }
                    }
                }
                if (isDynamic) {
                    continue
                }

                JavaParserMethodDeclaration methodDeclaration = (JavaParserMethodDeclaration) symbolReference.correspondingDeclaration
                boolean isConcreteClass = !Navigator.findClassOrInterfaceDeclarationExpression(methodDeclaration.wrappedNode).interface
                if (isConcreteClass) {
                    StringLiteralExpr stringLiteral = StringLiteralConcatenator.determineStringLiteral(methodDeclaration.wrappedNode, javaParserFacade)
                    for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                        innerPossibleList.add(stringLiteral)
                    }
                } else {
                    def alternatePossibleSet = new HashSet<List<StringLiteralExpr>>()
                    for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                        for (def method : CodeLocator.locateMethodImplementations(typeSolver, methodDeclaration)) {
                            StringLiteralExpr stringLiteral = StringLiteralConcatenator.determineStringLiteral(method, javaParserFacade)

                            List<StringLiteralExpr> alternateStringLiteralList = new ArrayList<>()
                            alternateStringLiteralList.addAll(innerPossibleList)
                            alternateStringLiteralList.add(stringLiteral)

                            alternatePossibleSet.add(alternateStringLiteralList)
                        }
                    }
                    innerPossibleSet = alternatePossibleSet
                }
            } else if (inner instanceof BinaryExpr) {
                //someone is doing string concatenation inside of an append method :/
                if (Navigator.isFullyStringLiteral(inner)) {
                    for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                        innerPossibleList.add(StringLiteralConcatenator.handleFullyStringLiteral(inner))
                    }
                } else {
                    for (Node binaryChild : inner.childrenNodes) {
                        if (binaryChild instanceof NameExpr) {
                            //solve variable
                            def ref = javaParserFacade.solve(binaryChild)
                            if (ref.solved) {
                                def varSolver = new VariableDomainResolver(typeSolver, javaParserFacade)
                                def resolvedVariable = varSolver.solveVariable(ref, binaryChild.range, false)
                                if (resolvedVariable.dynamicVariable) {
                                    for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                                        StringLiteralExpr literalExpr = new StringLiteralExpr(inner.toStringWithoutComments())
                                        literalExpr.setUserData(UserDataKeys.DYNAMIC_QUERY_KEY, new DynamicQuery())
                                        innerPossibleList.add(literalExpr)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (inner instanceof NameExpr) {
                //solve variable
                def ref = javaParserFacade.solve(inner)
                if (ref.solved) {
                    def varSolver = new VariableDomainResolver(typeSolver, javaParserFacade)
                    def resolvedVariable = varSolver.solveVariable(ref, inner.range, false)
                    if (resolvedVariable.dynamicVariable) {
                        for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                            StringLiteralExpr literalExpr = new StringLiteralExpr(inner.toStringWithoutComments())
                            literalExpr.setUserData(UserDataKeys.DYNAMIC_QUERY_KEY, new DynamicQuery())
                            innerPossibleList.add(literalExpr)
                        }
                    } else {
                        varSolver.possibleQueryList.each {
                            for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
                                innerPossibleList.add(it)
                            }
                        }
                    }
                }
            }
        }

        //merge all appends into one possible query (variations included)
        for (List<StringLiteralExpr> innerPossibleList : innerPossibleSet) {
            if (!innerPossibleList.empty) {
                def sb = new StringBuffer()
                boolean isDynamicQuery = false
                for (StringLiteralExpr literalExpr : innerPossibleList) {
                    sb.append(literalExpr.value)
                    if (literalExpr.getUserData(UserDataKeys.DYNAMIC_QUERY_KEY) != null) {
                        isDynamicQuery = true
                    }
                }
                StringLiteralExpr literalExpr = new StringLiteralExpr(sb.toString())
                if (isDynamicQuery) {
                    possibleDynamicQueryList.add(literalExpr)
                } else {
                    possibleQueryList.add(literalExpr)
                }
            }
        }
    }

    def getPossibleQueryList() {
        return possibleQueryList
    }

    def getPossibleDynamicQueryList() {
        return possibleDynamicQueryList
    }

}
