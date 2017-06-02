package com.codebrig.jnomad.utils

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.SourceCodeTypeSolver
import com.codebrig.jnomad.model.SourceCodeExtract
import com.github.javaparser.ParseProblemException
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.symbolsolver.javaparser.Navigator
import com.github.javaparser.symbolsolver.javaparsermodel.UnsolvedSymbolException
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration
import com.github.javaparser.symbolsolver.javassistmodel.JavassistClassDeclaration
import com.github.javaparser.symbolsolver.model.declarations.Declaration
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.github.javaparser.ast.Node

import java.util.concurrent.ConcurrentHashMap

/**
 * Queries can call methods on interfaces which return strings to be queried.
 * This will find classes which extend those interfaces and return strings.
 *
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class CodeLocator {

    private static Map<String, List<MethodDeclaration>> methodImplementationCache = new ConcurrentHashMap<>()

    static AssignExpr findAssignExpressionToTarget(Node node, Declaration targetDecleration, List<AssignExpr> exclusionList) {
        if (exclusionList == null) { exclusionList = new ArrayList<>() }
        if (node instanceof AssignExpr) {
            AssignExpr assignExpr = (AssignExpr) node
            NameExpr nameExpr = null
            if (assignExpr.getTarget() instanceof NameExpr && !targetDecleration.isField()) {
                nameExpr = (NameExpr) assignExpr.getTarget()
            } else if (assignExpr.getTarget() instanceof FieldAccessExpr && targetDecleration.isField()) {
                nameExpr = ((FieldAccessExpr) assignExpr.getTarget()).getFieldExpr()
            }

            boolean exclude = false
            for (AssignExpr expr : exclusionList) {
                if (nameExpr != null && nameExpr.getName().equals(targetDecleration.getName())) {
                    if (expr.equals(assignExpr) && expr.getRange().equals(assignExpr.getRange())) {
                        exclude = true
                    }
                }
            }
            if (nameExpr != null && nameExpr.getName().equals(targetDecleration.getName()) && !exclude) {
                exclusionList.add(assignExpr)
                return assignExpr
            }
        }
        for (Node child : node.getChildrenNodes()) {
            AssignExpr res = findAssignExpressionToTarget(child, targetDecleration, exclusionList)
            if (res != null) {
                return res
            }
        }
        return null
    }

    static List<Node> getStringBuilderInitAndAppendMethodCalls(Node node, String stringBuilderName) {
        boolean checkChildren = true
        List<Node> foundList = new ArrayList<>()
        if (node instanceof VariableDeclarator) {
            VariableDeclarator variableDeclarator = (VariableDeclarator) node
            if (stringBuilderName.equals(variableDeclarator.getId().getName())) {
                foundList.add(variableDeclarator)
            }
        } else if (node instanceof AssignExpr) {
            AssignExpr assignExpr = (AssignExpr) node
            if (assignExpr.getTarget() instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) assignExpr.getTarget()
                if (nameExpr.getName().equals(stringBuilderName)) {
                    foundList.add(assignExpr)
                }
            }
        } else if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = (MethodCallExpr) node
            if (methodCallExpr.getName().equals("append") &&  methodCallExpr.getScope().isPresent()) {
                if (methodCallExpr.getScope().get() instanceof MethodCallExpr) {
                    //stacked appends; add to found foundList in reverse order
                    List<Expression> tmpList = new ArrayList<>()
                    tmpList.add((Expression) methodCallExpr.getArgs().getChildrenNodes().get(0))
                    MethodCallExpr methodCall = methodCallExpr

                    //todo: fix do-while hack
                    methodCall = (MethodCallExpr) methodCall.getScope().get()
                    tmpList.add((Expression) methodCall.getArgs().getChildrenNodes().get(0))
                    while (methodCall.getScope().isPresent() && methodCall.getScope().get() instanceof MethodCallExpr) {
                        methodCall = (MethodCallExpr) methodCall.getScope().get()
                        tmpList.add((Expression) methodCall.getArgs().getChildrenNodes().get(0))
                    }

                    //now check name; if it's what we're looking for add to foundList in reverse
                    //no need to check children as we've handled the chain
                    NameExpr nameExpr = (NameExpr) methodCall.getScope().get()
                    if (nameExpr.getName().equals(stringBuilderName)) {
                        Collections.reverse(tmpList)
                        foundList.addAll(tmpList)
                        checkChildren = false
                    }
                } else {
                    NameExpr nameExpr = (NameExpr) methodCallExpr.getScope().get()
                    if (nameExpr.getName().equals(stringBuilderName)) {
                        Node n = methodCallExpr.getArgs()
                        foundList.add(n.getChildrenNodes().get(0))
                    }
                }
            }
        }

        if (checkChildren) {
            for (Node child : node.getChildrenNodes()) {
                foundList.addAll(getStringBuilderInitAndAppendMethodCalls(child, stringBuilderName))
            }
        }
        return foundList
    }

    static List<MethodCallExpr> getMethodCalls(Node node) {
        List<MethodCallExpr> methodCallExprList = new ArrayList<>()
        if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = (MethodCallExpr) node
            methodCallExprList.add(methodCallExpr)
        }
        for (Node child : node.getChildrenNodes()) {
            methodCallExprList.addAll(getMethodCalls(child))
        }
        return methodCallExprList
    }

    static MethodDeclaration getParentMethodDeclarationExpression(Node node) {
        if (node == null) {
            return null
        } else if (node instanceof MethodDeclaration) {
            return (MethodDeclaration) node
        }

        MethodDeclaration res = getParentMethodDeclarationExpression(node.getParentNode())
        if (res != null) {
            return res
        }
        return null
    }

    static CompilationUnit demandCompilationUnit(Node node) {
        if (node == null) {
            return null
        } else if (node instanceof CompilationUnit) {
            return (CompilationUnit) node
        }

        CompilationUnit res = demandCompilationUnit(node.getParentNode())
        if (res != null) {
            return res
        }
        return null
    }

    static ClassOrInterfaceDeclaration findClassOrInterfaceDeclarationExpression(Node node) {
        if (node == null) {
            return null
        } else if (node instanceof ClassOrInterfaceDeclaration) {
            return (ClassOrInterfaceDeclaration) node
        }

        ClassOrInterfaceDeclaration res = findClassOrInterfaceDeclarationExpression(node.getParentNode())
        if (res != null) {
            return res
        }
        return null
    }

    static List<MethodDeclaration> locateMethodImplementations(SourceCodeTypeSolver typeSolver, JavaParserMethodDeclaration methodDeclaration) {
        def qualifiedName = methodDeclaration.declaringType().qualifiedName
        def implMethodList = new ArrayList<>()
        if (methodImplementationCache.containsKey(qualifiedName + "." + methodDeclaration.name)) {
            implMethodList.addAll(methodImplementationCache.get(qualifiedName + "." + methodDeclaration.name))
        } else {
            def abstractClass = typeSolver.solveType(qualifiedName)
            for (String str : typeSolver.definedSourceCodeClassNames) {
                if (str.startsWith("javax.") || str.startsWith("java.") || str.startsWith("javassist.")) {
                    continue //don't need to check core Java classes
                }

                def solvedType = typeSolver.solveType(str)
                if (solvedType instanceof JavassistClassDeclaration) {
                    continue //can't get type declaration of JavaAssist classes
                }

                try {
                    if (solvedType.isClass() && solvedType.canBeAssignedTo(abstractClass)
                            && solvedType instanceof JavaParserClassDeclaration) {
                        def implMethodDeceleration = findMethodDeclarationExpression(
                                solvedType.wrappedNode, methodDeclaration.name)
                        implMethodList.add(implMethodDeceleration)
                    }
                } catch (UnsolvedSymbolException ex) {
                    //todo: these exceptions don't seem to be important but should look into again at some point
                }
            }
            methodImplementationCache.put(qualifiedName + "." + methodDeclaration.name, implMethodList)
        }
        return implMethodList
    }

    static MethodDeclaration findMethodDeclarationExpression(Node node, String name) {
        if (node instanceof MethodDeclaration) {
            MethodDeclaration nameExpr = (MethodDeclaration) node
            if (nameExpr.getName() != null && nameExpr.getName().equals(name)) {
                return nameExpr
            }
        }
        for (Node child : node.getChildrenNodes()) {
            MethodDeclaration res = findMethodDeclarationExpression(child, name)
            if (res != null) {
                return res
            }
        }
        return null
    }

    static ClassOrInterfaceDeclaration locateClassOrInterfaceDeclaration(SourceCodeTypeSolver typeSolver, ClassOrInterfaceType classOrInterfaceType) {
        try {
            def classList = typeSolver.definedClassNames
            for (String str : classList) {
                if (str.startsWith("javax.") || str.startsWith("java.") || str.startsWith("javassist.")) {
                    continue //don't need to check core Java classes
                } else if (!str.endsWith(classOrInterfaceType.name)) {
                    continue //not class we are looking for
                }

                def solvedType = typeSolver.solveType(str)
                if (solvedType instanceof JavassistClassDeclaration) {
                    //can't get type declaration of JavaAssist classes
                } else if (solvedType instanceof JavaParserClassDeclaration) {
                    return solvedType.wrappedNode
                }
            }
        } catch (ParseProblemException ex) {
            ex.printStackTrace()
        }
        return null
    }

    static List<ClassOrInterfaceType> locateClassExtensionPath(ClassOrInterfaceDeclaration classDeclaration) {
        def implMethodList = new ArrayList<>()
        for (ClassOrInterfaceType extendsType : classDeclaration.extends) {
            implMethodList.addAll(extendsType)
        }
        return implMethodList
    }

    static SourceCodeExtract getJPATableSourceCodeExtract(JNomad jNomad, String tableName) {
        for (SourceCodeExtract extract : jNomad.scannedFileList) {
            if (extract.JPATable && extract.queryTableAliasExtractor.tableNameAliasMap.containsValue(tableName)) {
                return extract
            }
        }
        return null
    }

    static SourceCodeExtract getJPATableSourceCodeExtract(JNomad jNomad, String tableName, List<SourceCodeExtract> excludeList) {
        for (SourceCodeExtract extract : jNomad.scannedFileList) {
            if (extract.JPATable && extract.queryTableAliasExtractor.tableNameAliasMap.containsValue(tableName)) {
                boolean isExcluded = false
                excludeList.each {
                    if (it == extract) {
                        isExcluded = true
                    }
                }
                if (!isExcluded) return extract
            }
        }
        return null
    }

    static SourceCodeExtract getJPAEmbeddableSourceCodeExtract(JNomad jNomad, String className) {
        return getJPAEmbeddableSourceCodeExtract(jNomad.scannedFileList, className)
    }

    static SourceCodeExtract getJPAEmbeddableSourceCodeExtract(List<SourceCodeExtract> scannedFileList, String className) {
        for (SourceCodeExtract extract : scannedFileList) {
            if (extract.JPAEmbeddable && extract.queryColumnAliasExtractor.className == className) {
                return extract
            }
        }
        return null
    }

    static List<File> findSourceFiles(File searchFile, List<File> queue, boolean recursive) {
        final FileFilter filter = new FileFilter() {
            @Override
            boolean accept(File file) {
                return file.isDirectory() || file.getName().endsWith(".java")
            }
        }

        //BFS recursive search for all .java files
        if (searchFile.isDirectory()) {
            //println "Searching directory: " + searchFile.absolutePath
            for (File childFile : searchFile.listFiles(filter)) {
                findSourceFiles(childFile, queue, recursive)
            }
        } else if (searchFile.exists()) {
            queue.add(searchFile)
        } else {
            println "Skipping invalid directory: " + searchFile.absolutePath
        }
        return queue
    }

    static List<File> findSourceDirectories(File searchDirectory, List<File> queue, boolean recursive) {
        final FileFilter filter = new FileFilter() {
            @Override
            boolean accept(File file) {
                return file.isDirectory()
            }
        }

        //BFS recursive search for all src/main/java directories
        if (searchDirectory.isDirectory()) {
            def srcMainJavaDir = new File(searchDirectory, "src/main/java")
            if (srcMainJavaDir.exists()) {
                queue.add(srcMainJavaDir)
            } else {
                for (File childFile : searchDirectory.listFiles(filter)) {
                    findSourceDirectories(childFile, queue, recursive)
                }
            }
        }
        return queue
    }

}
