/*
 * Copyright 2016 Federico Tomassetti
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.javaparser.symbolsolver.resolution;

import com.github.javaparser.symbolsolver.core.resolution.Context;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserEnumDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserInterfaceDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.javassistmodel.JavassistClassDeclaration;
import com.github.javaparser.symbolsolver.javassistmodel.JavassistInterfaceDeclaration;
import com.github.javaparser.symbolsolver.model.declarations.*;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.model.methods.MethodUsage;
import com.github.javaparser.symbolsolver.model.typesystem.*;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionClassDeclaration;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionInterfaceDeclaration;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import java.util.*;
import java.util.stream.Collectors;

public class MethodResolutionLogic {

    private static List<Type> groupVariadicParamValues(List<Type> argumentsTypes, int startVariadic, Type variadicType) {
        List<Type> res = new ArrayList<>(argumentsTypes.subList(0, startVariadic));
        List<Type> variadicValues = argumentsTypes.subList(startVariadic, argumentsTypes.size());
        if (variadicValues.isEmpty()) {
            // TODO if there are no variadic values we should default to the bound of the formal type
            res.add(variadicType);
        } else {
            Type componentType = findCommonType(variadicValues);
            res.add(new ArrayType(componentType));
        }
        return res;
    }

    private static Type findCommonType(List<Type> variadicValues) {
        if (variadicValues.isEmpty()) {
            throw new IllegalArgumentException();
        }
        // TODO implement this decently
        return variadicValues.get(0);
    }

    public static boolean isApplicable(MethodDeclaration method, String name, List<Type> argumentsTypes, TypeSolver typeSolver) {
        return isApplicable(method, name, argumentsTypes, typeSolver, false);
    }

    private static boolean isApplicable(MethodDeclaration method, String name, List<Type> argumentsTypes, TypeSolver typeSolver, boolean withWildcardTolerance) {
        if (!method.getName().equals(name)) {
            return false;
        }
        if (method.hasVariadicParameter()) {
            int pos = method.getNumberOfParams() - 1;
            if (method.getNumberOfParams() == argumentsTypes.size()) {
                // check if the last value is directly assignable as an array
                Type expectedType = method.getLastParam().getType();
                Type actualType = argumentsTypes.get(pos);
                if (!expectedType.isAssignableBy(actualType)) {
                    for (TypeParameterDeclaration tp : method.getTypeParameters()) {
                        expectedType = replaceTypeParam(expectedType, tp, typeSolver);
                    }
                    if (!expectedType.isAssignableBy(actualType)) {
                        if (actualType.isArray() && expectedType.isAssignableBy(actualType.asArrayType().getComponentType())) {
                            argumentsTypes.set(pos, actualType.asArrayType().getComponentType());
                        } else {
                            argumentsTypes = groupVariadicParamValues(argumentsTypes, pos, method.getLastParam().getType());
                        }
                    }
                } // else it is already assignable, nothing to do
            } else {
                argumentsTypes = groupVariadicParamValues(argumentsTypes, pos, method.getLastParam().getType());
            }
        }

        if (method.getNumberOfParams() != argumentsTypes.size()) {
            return false;
        }
        Map<String, Type> matchedParameters = new HashMap<>();
        boolean needForWildCardTolerance = false;
        for (int i = 0; i < method.getNumberOfParams(); i++) {
            List<TypeParameterDeclaration> typeParameters1 = method.getTypeParameters();
            ParameterDeclaration param = method.getParam(i);
            Type expectedType = method.getParam(i).getType();
            Type actualType = argumentsTypes.get(i);
            if ((expectedType.isTypeVariable() && !(expectedType.isWildcard())) && expectedType.asTypeParameter().declaredOnMethod()) {
                matchedParameters.put(expectedType.asTypeParameter().getName(), actualType);
                continue;
            }
            boolean isAssignableWithoutSubstitution = expectedType.isAssignableBy(actualType) ||
                    (method.getParam(i).isVariadic() && new ArrayType(expectedType).isAssignableBy(actualType));
            if (!isAssignableWithoutSubstitution && expectedType.isReferenceType() && actualType.isReferenceType()) {
                isAssignableWithoutSubstitution = isAssignableMatchTypeParameters(
                        expectedType.asReferenceType(),
                        actualType.asReferenceType(),
                        matchedParameters);
            }
            if (!isAssignableWithoutSubstitution) {
                List<TypeParameterDeclaration> typeParameters = method.getTypeParameters();
                typeParameters.addAll(method.declaringType().getTypeParameters());
                for (TypeParameterDeclaration tp : typeParameters) {
                    expectedType = replaceTypeParam(expectedType, tp, typeSolver);
                }

                if (!expectedType.isAssignableBy(actualType)) {
                    if (actualType.isWildcard() && withWildcardTolerance && !expectedType.isPrimitive()) {
                        needForWildCardTolerance = true;
                        continue;
                    }
                    if (method.hasVariadicParameter() && i == method.getNumberOfParams() - 1) {
                        if (new ArrayType(expectedType).isAssignableBy(actualType)) {
                            continue;
                        }
                    }
                    return false;
                }
            }
        }
        return !withWildcardTolerance || needForWildCardTolerance;
    }

    public static boolean isAssignableMatchTypeParameters(Type expected, Type actual,
                                                          Map<String, Type> matchedParameters) {
        if (expected.isReferenceType() && actual.isReferenceType()) {
            return isAssignableMatchTypeParameters(expected.asReferenceType(), actual.asReferenceType(), matchedParameters);
        } else if (expected.isTypeVariable()) {
            matchedParameters.put(expected.asTypeParameter().getName(), actual);
            return true;
        } else {
            throw new UnsupportedOperationException(expected.getClass().getCanonicalName() + " " + actual.getClass().getCanonicalName());
        }
    }

    public static boolean isAssignableMatchTypeParameters(ReferenceType expected, ReferenceType actual,
                                                          Map<String, Type> matchedParameters) {
        if (actual.getQualifiedName().equals(expected.getQualifiedName())) {
            return isAssignableMatchTypeParametersMatchingQName(expected, actual, matchedParameters);
        } else {
            List<ReferenceType> ancestors = actual.getAllAncestors();
            for (ReferenceType ancestor : ancestors) {
                if (isAssignableMatchTypeParametersMatchingQName(expected, ancestor, matchedParameters)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isAssignableMatchTypeParametersMatchingQName(ReferenceType expected, ReferenceType actual,
                                                                        Map<String, Type> matchedParameters) {

        if (!expected.getQualifiedName().equals(actual.getQualifiedName())) {
            return false;
        }
        if (expected.typeParametersValues().size() != actual.typeParametersValues().size()) {
            throw new UnsupportedOperationException();
            //return true;
        }
        for (int i = 0; i < expected.typeParametersValues().size(); i++) {
            Type expectedParam = expected.typeParametersValues().get(i);
            Type actualParam = actual.typeParametersValues().get(i);
            if (expectedParam.isTypeVariable()) {
                String expectedParamName = expectedParam.asTypeParameter().getName();
                if (!actualParam.isTypeVariable() || !actualParam.asTypeParameter().getName().equals(expectedParamName)) {
                    if (matchedParameters.containsKey(expectedParamName)) {
                        Type matchedParameter = matchedParameters.get(expectedParamName);
                        if (matchedParameter.isAssignableBy(actualParam)) {
                            return true;
                        } else if (actualParam.isAssignableBy(matchedParameter)) {
                            matchedParameters.put(expectedParamName, actualParam);
                            return true;
                        }
                        return false;
                    } else {
                        matchedParameters.put(expectedParamName, actualParam);
                    }
                }
            } else if (expectedParam.isReferenceType()) {
                if (!expectedParam.equals(actualParam)) {
                    return false;
                }
            } else if (expectedParam.isWildcard()) {
                if (expectedParam.asWildcard().isExtends()) {
                    return isAssignableMatchTypeParameters(expectedParam.asWildcard().getBoundedType(), actual, matchedParameters);
                }
                // TODO verify super bound
                return true;
            } else {
                throw new UnsupportedOperationException(expectedParam.describe());
            }
        }
        return true;
    }

    public static Type replaceTypeParam(Type type, TypeParameterDeclaration tp, TypeSolver typeSolver) {
        if (type.isTypeVariable()) {
            if (type.describe().equals(tp.getName())) {
                List<TypeParameterDeclaration.Bound> bounds = tp.getBounds(typeSolver);
                if (bounds.size() > 1) {
                    throw new UnsupportedOperationException();
                } else if (bounds.size() == 1) {
                    return bounds.get(0).getType();
                } else {
                    return new ReferenceTypeImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver);
                }
            }
            return type;
        } else if (type.isPrimitive()) {
            return type;
        } else if (type.isArray()) {
            return new ArrayType(replaceTypeParam(type.asArrayType().getComponentType(), tp, typeSolver));
        } else if (type.isReferenceType()) {
            ReferenceType result = type.asReferenceType();
            result = result.transformTypeParameters(typeParam -> replaceTypeParam(typeParam, tp, typeSolver)).asReferenceType();
            return result;
        } else if (type.isWildcard()) {
            if (type.describe().equals(tp.getName())) {
                List<TypeParameterDeclaration.Bound> bounds = tp.getBounds(typeSolver);
                if (bounds.size() > 1) {
                    throw new UnsupportedOperationException();
                } else if (bounds.size() == 1) {
                    return bounds.get(0).getType();
                } else {
                    return new ReferenceTypeImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver);
                }
            }
            return type;
        } else {
            throw new UnsupportedOperationException("Replacing " + type + ", param " + tp + " with " + type.getClass().getCanonicalName());
        }
    }

    public static boolean isApplicable(MethodUsage method, String name, List<Type> argumentsTypes, TypeSolver typeSolver) {
        if (!method.getName().equals(name)) {
            return false;
        }
        // TODO Consider varargs
        if (method.getNoParams() != argumentsTypes.size()) {
            return false;
        }
        for (int i = 0; i < method.getNoParams(); i++) {
            Type expectedType = method.getParamType(i);
            Type expectedTypeWithoutSubstitutions = expectedType;
            Type actualType = argumentsTypes.get(i);

            List<TypeParameterDeclaration> typeParameters = method.getDeclaration().getTypeParameters();
            typeParameters.addAll(method.declaringType().getTypeParameters());
            for (TypeParameterDeclaration tp : typeParameters) {
                if (tp.getBounds(typeSolver).isEmpty()) {
                    //expectedType = expectedType.replaceTypeVariables(tp.getName(), new ReferenceTypeUsageImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver));
                    expectedType = expectedType.replaceTypeVariables(tp, Wildcard.extendsBound(new ReferenceTypeImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver)));
                } else if (tp.getBounds(typeSolver).size() == 1) {
                    TypeParameterDeclaration.Bound bound = tp.getBounds(typeSolver).get(0);
                    if (bound.isExtends()) {
                        //expectedType = expectedType.replaceTypeVariables(tp.getName(), bound.getType());
                        expectedType = expectedType.replaceTypeVariables(tp, Wildcard.extendsBound(bound.getType()));
                    } else {
                        //expectedType = expectedType.replaceTypeVariables(tp.getName(), new ReferenceTypeUsageImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver));
                        expectedType = expectedType.replaceTypeVariables(tp, Wildcard.superBound(bound.getType()));
                    }
                } else {
                    throw new UnsupportedOperationException();
                }
            }
            Type expectedType2 = expectedTypeWithoutSubstitutions;
            for (TypeParameterDeclaration tp : typeParameters) {
                if (tp.getBounds(typeSolver).isEmpty()) {
                    expectedType2 = expectedType2.replaceTypeVariables(tp, new ReferenceTypeImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver));
                } else if (tp.getBounds(typeSolver).size() == 1) {
                    TypeParameterDeclaration.Bound bound = tp.getBounds(typeSolver).get(0);
                    if (bound.isExtends()) {
                        expectedType2 = expectedType2.replaceTypeVariables(tp, bound.getType());
                    } else {
                        expectedType2 = expectedType2.replaceTypeVariables(tp, new ReferenceTypeImpl(typeSolver.solveType(Object.class.getCanonicalName()), typeSolver));
                    }
                } else {
                    throw new UnsupportedOperationException();
                }
            }
            if (!expectedType.isAssignableBy(actualType)
                    && !expectedType2.isAssignableBy(actualType)
                    && !expectedTypeWithoutSubstitutions.isAssignableBy(actualType)) {
                return false;
            }
        }
        return true;
    }

    private static List<MethodDeclaration> getMethodsWithoutDuplicates(List<MethodDeclaration> methods) {
        Set<MethodDeclaration> s = new TreeSet<MethodDeclaration>(new Comparator<MethodDeclaration>() {
            @Override
            public int compare(MethodDeclaration m1, MethodDeclaration m2) {
                if (m1 instanceof JavaParserMethodDeclaration && m2 instanceof JavaParserMethodDeclaration &&
                        ((JavaParserMethodDeclaration) m1).getWrappedNode().equals(((JavaParserMethodDeclaration) m2).getWrappedNode())) {
                    return 0;
                }
                return 1;
            }
        });
        s.addAll(methods);
        List<MethodDeclaration> res = new ArrayList<>();
        Set<String> usedSignatures = new HashSet<>();
        for (MethodDeclaration md : methods) {
            String signature = md.getQualifiedSignature();
            if (!usedSignatures.contains(signature)) {
                usedSignatures.add(signature);
                res.add(md);
            }
        }
        return res;
    }

    /**
     * @param methods we expect the methods to be ordered such that inherited methods are later in the list
     * @param name
     * @param argumentsTypes
     * @param typeSolver
     * @return
     */
    public static SymbolReference<MethodDeclaration> findMostApplicable(List<MethodDeclaration> methods, String name, List<Type> argumentsTypes, TypeSolver typeSolver) {
        SymbolReference<MethodDeclaration> res = findMostApplicable(methods, name, argumentsTypes, typeSolver, false);
        if (res.isSolved()) {
            return res;
        }
        return findMostApplicable(methods, name, argumentsTypes, typeSolver, true);
    }

    public static SymbolReference<MethodDeclaration> findMostApplicable(List<MethodDeclaration> methods, String name, List<Type> argumentsTypes, TypeSolver typeSolver, boolean wildcardTolerance) {
        List<MethodDeclaration> applicableMethods = getMethodsWithoutDuplicates(methods).stream().filter((m) -> isApplicable(m, name, argumentsTypes, typeSolver, wildcardTolerance)).collect(Collectors.toList());
        if (applicableMethods.isEmpty()) {
            return SymbolReference.unsolved(MethodDeclaration.class);
        }
        if (applicableMethods.size() == 1) {
            return SymbolReference.solved(applicableMethods.get(0));
        } else {
            MethodDeclaration winningCandidate = applicableMethods.get(0);
            for (int i = 1; i < applicableMethods.size(); i++) {
                MethodDeclaration other = applicableMethods.get(i);
                if (isMoreSpecific(winningCandidate, other, typeSolver)) {
                    // nothing to do
                } else if (isMoreSpecific(other, winningCandidate, typeSolver)) {
                    winningCandidate = other;
                } else {
                    if (winningCandidate.declaringType().getQualifiedName().equals(other.declaringType().getQualifiedName())) {
                        throw new MethodAmbiguityException("Ambiguous method call: cannot find a most applicable method: " + winningCandidate + ", " + other);
                    } else {
                        // we expect the methods to be ordered such that inherited methods are later in the list
                    }
                }
            }
            return SymbolReference.solved(winningCandidate);
        }
    }

    private static boolean isMoreSpecific(MethodDeclaration methodA, MethodDeclaration methodB, TypeSolver typeSolver) {
        boolean oneMoreSpecificFound = false;
        if (methodA.getNumberOfParams() < methodB.getNumberOfParams()) {
            return true;
        }
        if (methodA.getNumberOfParams() > methodB.getNumberOfParams()) {
            return false;
        }
        for (int i = 0; i < methodA.getNumberOfParams(); i++) {
            Type tdA = methodA.getParam(i).getType();
            Type tdB = methodB.getParam(i).getType();
            // B is more specific
            if (tdB.isAssignableBy(tdA) && !tdA.isAssignableBy(tdB)) {
                oneMoreSpecificFound = true;
            }
            // A is more specific
            if (tdA.isAssignableBy(tdB) && !tdB.isAssignableBy(tdA)) {
                return false;
            }
            // if it matches a variadic and a not variadic I pick the not variadic
            // FIXME
            if (i == (methodA.getNumberOfParams() - 1) && tdA.arrayLevel() > tdB.arrayLevel()) {
                return true;
            }
        }
        return oneMoreSpecificFound;
    }

    private static boolean isMoreSpecific(MethodUsage methodA, MethodUsage methodB, TypeSolver typeSolver) {
        boolean oneMoreSpecificFound = false;
        for (int i = 0; i < methodA.getNoParams(); i++) {
            Type tdA = methodA.getParamType(i);
            Type tdB = methodB.getParamType(i);

            boolean aIsAssignableByB = tdA.isAssignableBy(tdB);
            boolean bIsAssignableByA = tdB.isAssignableBy(tdA);

            // B is more specific
            if (bIsAssignableByA && !aIsAssignableByB) {
                oneMoreSpecificFound = true;
            }
            // A is more specific
            if (aIsAssignableByB && !bIsAssignableByA) {
                return false;
            }
        }
        return oneMoreSpecificFound;
    }

    public static Optional<MethodUsage> findMostApplicableUsage(List<MethodUsage> methods, String name, List<Type> argumentsTypes, TypeSolver typeSolver) {
        List<MethodUsage> applicableMethods = methods.stream().filter((m) -> isApplicable(m, name, argumentsTypes, typeSolver)).collect(Collectors.toList());
        if (applicableMethods.isEmpty()) {
            return Optional.empty();
        }
        if (applicableMethods.size() == 1) {
            return Optional.of(applicableMethods.get(0));
        } else {
            MethodUsage winningCandidate = applicableMethods.get(0);
            for (int i = 1; i < applicableMethods.size(); i++) {
                MethodUsage other = applicableMethods.get(i);
                if (isMoreSpecific(winningCandidate, other, typeSolver)) {
                    // nothing to do
                } else if (isMoreSpecific(other, winningCandidate, typeSolver)) {
                    winningCandidate = other;
                } else {
                    if (winningCandidate.declaringType().getQualifiedName().equals(other.declaringType().getQualifiedName())) {
                        if (!areOverride(winningCandidate, other)) {
                            throw new MethodAmbiguityException("Ambiguous method call: cannot find a most applicable method: " + winningCandidate + ", " + other + ". First declared in " + winningCandidate.declaringType().getQualifiedName());
                        }
                    } else {
                        // we expect the methods to be ordered such that inherited methods are later in the list
                        //throw new UnsupportedOperationException();
                    }
                }
            }
            return Optional.of(winningCandidate);
        }
    }

    private static boolean areOverride(MethodUsage winningCandidate, MethodUsage other) {
        if (!winningCandidate.getName().equals(other.getName())) {
            return false;
        }
        if (winningCandidate.getNoParams() != other.getNoParams()) {
            return false;
        }
        for (int i = 0; i < winningCandidate.getNoParams(); i++) {
            if (!winningCandidate.getParamTypes().get(i).equals(other.getParamTypes().get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Replace TypeDeclaration.solveMethod
     *
     * @param typeDeclaration
     * @param name
     * @param argumentsTypes
     * @return
     */
    public static SymbolReference<MethodDeclaration> solveMethodInType(TypeDeclaration typeDeclaration, String name, List<Type> argumentsTypes, TypeSolver typeSolver) {
        if (typeDeclaration instanceof JavaParserClassDeclaration) {
            Context ctx = ((JavaParserClassDeclaration) typeDeclaration).getContext();
            return ctx.solveMethod(name, argumentsTypes, typeSolver);
        }
        if (typeDeclaration instanceof JavaParserInterfaceDeclaration) {
            Context ctx = ((JavaParserInterfaceDeclaration) typeDeclaration).getContext();
            return ctx.solveMethod(name, argumentsTypes, typeSolver);
        }
        if (typeDeclaration instanceof JavaParserEnumDeclaration) {
            if (name.equals("values") && argumentsTypes.isEmpty()) {
                return SymbolReference.solved(new JavaParserEnumDeclaration.ValuesMethod((JavaParserEnumDeclaration) typeDeclaration, typeSolver));
            }
            Context ctx = ((JavaParserEnumDeclaration) typeDeclaration).getContext();
            return ctx.solveMethod(name, argumentsTypes, typeSolver);
        }
        if (typeDeclaration instanceof ReflectionClassDeclaration) {
            return ((ReflectionClassDeclaration) typeDeclaration).solveMethod(name, argumentsTypes);
        }
        if (typeDeclaration instanceof ReflectionInterfaceDeclaration) {
            return ((ReflectionInterfaceDeclaration) typeDeclaration).solveMethod(name, argumentsTypes);
        }
        if (typeDeclaration instanceof JavassistInterfaceDeclaration) {
            return ((JavassistInterfaceDeclaration) typeDeclaration).solveMethod(name, argumentsTypes);
        }
        if (typeDeclaration instanceof JavassistClassDeclaration) {
            return ((JavassistClassDeclaration) typeDeclaration).solveMethod(name, argumentsTypes);
        }
        throw new UnsupportedOperationException(typeDeclaration.getClass().getCanonicalName());
    }

}
