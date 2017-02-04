package com.codebrig.jnomad.utils

import com.codebrig.jnomad.task.resolve.VariableDomainResolver
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.ReturnStmt
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.github.javaparser.symbolsolver.javaparser.Navigator
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference

/**
 * This class walks AST node structure and concatenates string literals.
 *
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class StringLiteralConcatenator {

    static StringLiteralExpr simpleConcatenate(List<StringLiteralExpr> stringLiteralList) {
        def sb = new StringBuffer()
        for (StringLiteralExpr stringLiteral : stringLiteralList) {
            sb.append(stringLiteral.value)
        }
        return new StringLiteralExpr(sb.toString())
    }

    static StringLiteralExpr handleFullyStringLiteral(Expression fullyStringExpression) {
        List<StringLiteralExpr> stringLiteralList = new ArrayList<>()
        for (def childNode : fullyStringExpression.childrenNodes) {
            if (childNode instanceof BinaryExpr) {
                def stringLiteral = handleFullyStringLiteral(childNode)
                stringLiteralList.add(stringLiteral)
            } else if (childNode instanceof StringLiteralExpr) {
                stringLiteralList.add(childNode)
            }
        }

        def sb = new StringBuffer()
        for (StringLiteralExpr stringLiteral : stringLiteralList) {
            sb.append(stringLiteral.value)
        }
        return new StringLiteralExpr(sb.toString())
    }

    static StringLiteralExpr concatStringLiteral(BinaryExpr binaryExpr, MethodDeclaration methodDeclaration, JavaParserFacade parserFacade) {
        TestVisitor testVisitor = new TestVisitor(binaryExpr.getNodesByType(StringLiteralExpr.class).get(0))
        methodDeclaration.accept(testVisitor, parserFacade)

        def sb = new StringBuffer()
        for (StringLiteralExpr stringLiteral : testVisitor.concatStringLiteralList) {
            sb.append(stringLiteral.value)
        }

        return new StringLiteralExpr(sb.toString())
    }

    static StringLiteralExpr concatStringLiteral(StringLiteralExpr literalExpr, MethodDeclaration methodDeclaration, JavaParserFacade parserFacade) {
        TestVisitor testVisitor = new TestVisitor(literalExpr)
        methodDeclaration.accept(testVisitor, parserFacade)

        def sb = new StringBuffer()
        for (StringLiteralExpr stringLiteral : testVisitor.concatStringLiteralList) {
            sb.append(stringLiteral.value)
        }

        return new StringLiteralExpr(sb.toString())
    }

    static StringLiteralExpr determineStringLiteral(MethodDeclaration methodDeclaration, JavaParserFacade parserFacade) {
        TestVisitor testVisitor = new TestVisitor()
        methodDeclaration.accept(testVisitor, parserFacade)

        def sb = new StringBuffer()
        for (StringLiteralExpr stringLiteral : testVisitor.concatStringLiteralList) {
            sb.append(stringLiteral.value)
        }

        return new StringLiteralExpr(sb.toString())
    }

    static boolean isFullyStringLiteral(Node node) {
        if (!(node instanceof BinaryExpr || node instanceof StringLiteralExpr)) {
            return false
        }
        for (Node child : node.getChildrenNodes()) {
            boolean res = isFullyStringLiteral(child)
            if (!res) {
                return false
            }
        }
        return true
    }

    /**
     * This visitor is only called on methods which return a String
     */
    static class TestVisitor extends VoidVisitorAdapter<JavaParserFacade> {

        private StringLiteralExpr stringLiteral
        private List<StringLiteralExpr> concatStringLiteralList = new ArrayList<>()
        private boolean dynamicVariable

        TestVisitor() {
        }

        TestVisitor(StringLiteralExpr stringLiteral) {
            this.stringLiteral = stringLiteral
            this.concatStringLiteralList.add(stringLiteral)
        }

        @Override
        void visit(ReturnStmt n, JavaParserFacade javaParserFacade) {
            super.visit(n, javaParserFacade)

            for (def child : n.childrenNodes) {
                if (child instanceof StringLiteralExpr) {
                    //returns string literal
                    concatStringLiteralList.add(child)
                } else if (child instanceof BinaryExpr) {
                    //returns binary expression
                    handleBinaryExpr(child.left, javaParserFacade)
                    handleBinaryExpr(child.right, javaParserFacade)
                } else if (child instanceof NameExpr) {
                    //returns variable
                    def ref = javaParserFacade.solve(child)
                    if (ref.isSolved()) {
                        def variableResolver = new VariableDomainResolver(javaParserFacade.getTypeSolver(), javaParserFacade)
                        def solvedVariable = variableResolver.solveVariable(ref, child.range, false)
                        if (solvedVariable.dynamicVariable) {
                            //return nothing; it's dynamic
                            dynamicVariable = true
                            return
                        }
                    }
                } else if (child instanceof MethodCallExpr) {
                    def ref = javaParserFacade.solve(child)
                    if (ref.solved) {
                        def declaration = (com.github.javaparser.symbolsolver.model.declarations.MethodDeclaration) ref.correspondingDeclaration
                        def stringBufferType = javaParserFacade.getTypeSolver().solveType("java.lang.StringBuffer")
                        def stringBuilderType = javaParserFacade.getTypeSolver().solveType("java.lang.StringBuilder")
                        if (declaration.declaringType().qualifiedName == stringBufferType.qualifiedName
                                || declaration.declaringType().qualifiedName == stringBuilderType.qualifiedName) {
                            //returns string builder
                            def strNameExpr = (NameExpr) child.childrenNodes.get(0)
                            ref = javaParserFacade.solve(strNameExpr)
                            def variableResolver = new VariableDomainResolver(javaParserFacade.getTypeSolver(), javaParserFacade)
                            variableResolver.solveVariable(ref, strNameExpr.range, true)
                            println variableResolver
                        } else {
                            if (declaration instanceof JavaParserMethodDeclaration) {
                                //we need to go deeper
                                declaration.wrappedNode.accept(new TestVisitor(stringLiteral), javaParserFacade)
                            } else {
                                //todo: dynamic maybe
                            }
                        }
                    } else {
                        //todo: dynamic maybe
                    }
                }
            }
        }

        private void handleBinaryExpr(Expression expression, JavaParserFacade javaParserFacade) {
            if (expression instanceof MethodCallExpr) {
                SymbolReference test2 = javaParserFacade.solve(expression)
                JavaParserMethodDeclaration methodDeclaration = (JavaParserMethodDeclaration) test2.correspondingDeclaration
                boolean isConcreteClass = !Navigator.findClassOrInterfaceDeclarationExpression(methodDeclaration.wrappedNode).interface
                if (isConcreteClass) {
                    StringLiteralExpr stringLiteral = determineStringLiteral(methodDeclaration.wrappedNode, javaParserFacade)
                    concatStringLiteralList.add(stringLiteral)
                } else {
                    for (def method : CodeLocator.locateMethodImplementations(javaParserFacade.typeSolver, methodDeclaration)) {
                        StringLiteralExpr stringLiteral = determineStringLiteral(method, javaParserFacade)
                        concatStringLiteralList.add(stringLiteral)
                    }
                }
            } else if (expression instanceof StringLiteralExpr) {
                concatStringLiteralList.add(expression)
            }
        }

        @Override
        void visit(MethodCallExpr n, JavaParserFacade javaParserFacade) {
            if (dynamicVariable) return
            super.visit(n, javaParserFacade)

            try {
                //explore method call
                for (def childNode : n.childrenNodes) {
                    childNode.accept(new TestVisitor(stringLiteral), javaParserFacade)
                }
            } catch (Exception ex) {
                println ex.printStackTrace()
            }
        }

        List<StringLiteralExpr> getConcatStringLiteralList() {
            if (dynamicVariable) return Collections.emptyList()
            return concatStringLiteralList
        }
    }

}
