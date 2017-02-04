package com.codebrig.jnomad.utils

import com.codebrig.jnomad.JNomad
import com.codebrig.jnomad.model.SourceCodeExtract
import com.github.javaparser.ParseProblemException
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.symbolsolver.javaparser.Navigator
import com.github.javaparser.symbolsolver.javaparsermodel.UnsolvedSymbolException
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration
import com.github.javaparser.symbolsolver.javassistmodel.JavassistClassDeclaration
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver

import java.util.concurrent.ConcurrentHashMap

/**
 * Queries can call methods on interfaces which return strings to be queried.
 * This will find classes which extend those interfaces and return strings.
 *
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class CodeLocator {

    private static Map<String, List<MethodDeclaration>> methodImplementationCache = new ConcurrentHashMap<>()

    static List<MethodDeclaration> locateMethodImplementations(TypeSolver typeSolver, JavaParserMethodDeclaration methodDeclaration) {
        def qualifiedName = methodDeclaration.declaringType().qualifiedName
        def implMethodList = new ArrayList<>()
        if (methodImplementationCache.containsKey(qualifiedName + "." + methodDeclaration.name)) {
            implMethodList.addAll(methodImplementationCache.get(qualifiedName + "." + methodDeclaration.name))
        } else {
            def abstractClass = typeSolver.solveType(qualifiedName)
            def classList = typeSolver.allDefinedClassNames

            for (String str : classList) {
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
                        def implMethodDeceleration = Navigator.findMethodDeclarationExpression(
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

    static ClassOrInterfaceDeclaration locateClassOrInterfaceDeclaration(TypeSolver typeSolver, ClassOrInterfaceType classOrInterfaceType) {
        try {
            def classList = typeSolver.allDefinedClassNames
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
        for (SourceCodeExtract extract : jNomad.scannedFileList) {
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
