package com.codebrig.jnomad

import com.codebrig.jnomad.utils.CodeLocator
import com.github.javaparser.JavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.symbolsolver.javaparser.Navigator
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import javassist.ClassPool
import javassist.NotFoundException
import com.google.common.io.Files

import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * @author Brandon Fergerson <brandon.fergerson@codebrig.com>
 */
class SourceCodeTypeSolver extends CombinedTypeSolver {

    private List<String> definedClassNameList = new ArrayList<>()
    private final Cache<String, SymbolReference<com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration>> solvedTypeCache = CacheBuilder.newBuilder().build()

    SourceCodeTypeSolver(TypeSolver... elements) {
        super(elements)
    }

    @Override
    SymbolReference<com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration> tryToSolveType(String name) {
        SymbolReference<com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration> cache = solvedTypeCache.getIfPresent(name)
        if (cache != null) {
            return cache
        }

        SymbolReference<com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration> type = super.tryToSolveType(name)
        if (type.solved) {
            solvedTypeCache.put(name, type)
        }
        return type
    }

    void addJavaParserTypeSolver(File srcDir) {
        add(new JavaParserTypeSolver(srcDir))

        List<File> queue = CodeLocator.findSourceFiles(srcDir, new ArrayList<>(), true);
        for (File f : queue) {
            try {
                CompilationUnit compilationUnit = JavaParser.parse(f)
                Optional<TypeDeclaration<?>> astTypeDeclaration = Navigator.findType(compilationUnit, Files.getNameWithoutExtension(f.getName()))
                if (!compilationUnit.getPackage().isPresent() || !astTypeDeclaration.isPresent()) {
                    continue
                }
                String className = compilationUnit.getPackage().get().getName().getQualifiedName() + "." + astTypeDeclaration.get().getNameExpr().getQualifiedName()
                definedClassNameList.add(className)
            } catch (FileNotFoundException ex) {
                ex.printStackTrace()
            }
        }
    }

    void addJarTypeSolver(String pathToJar) {
        add(new JarTypeSolver(pathToJar))

        try {
            ClassPool classPool = new ClassPool(false)
            classPool.appendClassPath(pathToJar)
            classPool.appendSystemPath()
        } catch (NotFoundException e) {
            throw new RuntimeException(e)
        }
        JarFile jarFile = new JarFile(pathToJar)
        JarEntry entry = null
        for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements(); entry = e.nextElement()) {
            if (entry != null && !entry.isDirectory() && entry.getName().endsWith(".class")) {
                String name = entryPathToClassName(entry.getName())
                definedClassNameList.add(name)
            }
        }
    }

    List<String> getDefinedClassNames() {
        return new ArrayList<>(definedClassNameList)
    }

    @Override
    void add(TypeSolver typeSolver) {
        super.add(typeSolver)
    }

    private static String entryPathToClassName(String entryPath) {
        if (!entryPath.endsWith(".class")) {
            throw new IllegalStateException()
        }

        String className = entryPath.substring(0, entryPath.length() - ".class".length())
        className = className.replaceAll("/", ".")
        className = className.replaceAll("\\\$", ".")
        return className
    }

}
