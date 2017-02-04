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

package com.github.javaparser.symbolsolver.javaparser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.symbolsolver.model.declarations.*;

import java.util.*;

/**
 * This class can be used to easily retrieve nodes from a JavaParser AST.
 */
public final class Navigator {

    private Navigator() {
        // prevent instantiation
    }

    private static String getOuterTypeName(String qualifiedName) {
        return qualifiedName.split("\\.", 2)[0];
    }

    public static Node getParentNode(Node node) {
        Node parent = node.getParentNode();
        if (parent instanceof NodeList) {
            return Navigator.getParentNode(parent);
        } else {
            return parent;
        }
    }

    private static String getInnerTypeName(String qualifiedName) {
        if (qualifiedName.contains(".")) {
            return qualifiedName.split("\\.", 2)[1];
        }
        return "";
    }

    public static Optional<TypeDeclaration<?>> findType(CompilationUnit cu, String qualifiedName) {
        if (cu.getTypes().isEmpty()) {
            return Optional.empty();
        }

        final String typeName = getOuterTypeName(qualifiedName);
        Optional<TypeDeclaration<?>> type = cu.getTypes().stream().filter((t) -> t.getName().equals(typeName)).findFirst();

        final String innerTypeName = getInnerTypeName(qualifiedName);
        if (type.isPresent() && !innerTypeName.isEmpty()) {
            return findType(type.get(), innerTypeName);
        }
        return type;
    }

    public static Optional<TypeDeclaration<?>> findType(TypeDeclaration<?> td, String qualifiedName) {
        final String typeName = getOuterTypeName(qualifiedName);

        Optional<TypeDeclaration<?>> type = Optional.empty();
        for (Node n : td.getMembers().getChildrenNodes()) {
            if (n instanceof TypeDeclaration && ((TypeDeclaration<?>) n).getName().equals(typeName)) {
                type = Optional.of((TypeDeclaration<?>) n);
                break;
            }
        }
        final String innerTypeName = getInnerTypeName(qualifiedName);
        if (type.isPresent() && !innerTypeName.isEmpty()) {
            return findType(type.get(), innerTypeName);
        }
        return type;
    }


    public static ClassOrInterfaceDeclaration demandClass(CompilationUnit cu, String qualifiedName) {
        ClassOrInterfaceDeclaration cd = demandClassOrInterface(cu, qualifiedName);
        if (cd.isInterface()) {
            throw new IllegalStateException("Type is not a class");
        }
        return cd;
    }

    public static EnumDeclaration demandEnum(CompilationUnit cu, String qualifiedName) {
        Optional<TypeDeclaration<?>> res = findType(cu, qualifiedName);
        if (!res.isPresent()) {
            throw new IllegalStateException("No type found");
        }
        if (!(res.get() instanceof EnumDeclaration)) {
            throw new IllegalStateException("Type is not an enum");
        }
        return (EnumDeclaration) res.get();
    }

    public static MethodDeclaration demandMethod(TypeDeclaration<?> cd, String name) {
        MethodDeclaration found = null;
        for (BodyDeclaration<?> bd : cd.getMembers()) {
            if (bd instanceof MethodDeclaration) {
                MethodDeclaration md = (MethodDeclaration) bd;
                if (md.getName().equals(name)) {
                    if (found != null) {
                        throw new IllegalStateException("Ambiguous getName");
                    }
                    found = md;
                }
            }
        }
        if (found == null) {
            throw new IllegalStateException("No method with given name");
        }
        return found;
    }

    public static VariableDeclarator demandField(ClassOrInterfaceDeclaration cd, String name) {
        for (BodyDeclaration<?> bd : cd.getMembers()) {
            if (bd instanceof FieldDeclaration) {
                FieldDeclaration fd = (FieldDeclaration) bd;
                for (VariableDeclarator vd : fd.getVariables()) {
                    if (vd.getId().getName().equals(name)) {
                        return vd;
                    }
                }
            }
        }
        throw new IllegalStateException("No field with given name");
    }

    public static NameExpr findNameExpression(Node node, String name) {
        if (node instanceof NameExpr) {
            NameExpr nameExpr = (NameExpr) node;
            if (nameExpr.getName() != null && nameExpr.getName().equals(name)) {
                return nameExpr;
            }
        }
        for (Node child : node.getChildrenNodes()) {
            NameExpr res = findNameExpression(child, name);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    public static MethodDeclaration findMethodDeclarationExpression(Node node, String name) {
        if (node instanceof MethodDeclaration) {
            MethodDeclaration nameExpr = (MethodDeclaration) node;
            if (nameExpr.getName() != null && nameExpr.getName().equals(name)) {
                return nameExpr;
            }
        }
        for (Node child : node.getChildrenNodes()) {
            MethodDeclaration res = findMethodDeclarationExpression(child, name);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    public static MethodDeclaration getParentMethodDeclerationExpression(Node node) {
        if (node == null) {
            return null;
        } else if (node instanceof MethodDeclaration) {
            return (MethodDeclaration) node;
        }

        MethodDeclaration res = getParentMethodDeclerationExpression(node.getParentNode());
        if (res != null) {
            return res;
        }
        return null;
    }

    public static CompilationUnit demandCompilationUnit(Node node) {
        if (node == null) {
            return null;
        } else if (node instanceof CompilationUnit) {
            return (CompilationUnit) node;
        }

        CompilationUnit res = demandCompilationUnit(node.getParentNode());
        if (res != null) {
            return res;
        }
        return null;
    }

    public static ClassOrInterfaceDeclaration findClassOrInterfaceDeclarationExpression(Node node) {
        if (node == null) {
            return null;
        } else if (node instanceof ClassOrInterfaceDeclaration) {
            return (ClassOrInterfaceDeclaration) node;
        }

        ClassOrInterfaceDeclaration res = findClassOrInterfaceDeclarationExpression(node.getParentNode());
        if (res != null) {
            return res;
        }
        return null;
    }

    public static AssignExpr findAssignExpressionToTarget(Node node, Declaration targetDecleration, List<AssignExpr> exclusionList) {
        if (exclusionList == null) { exclusionList = new ArrayList<>(); }
        if (node instanceof AssignExpr) {
            AssignExpr assignExpr = (AssignExpr) node;
            NameExpr nameExpr = null;
            if (assignExpr.getTarget() instanceof NameExpr && !targetDecleration.isField()) {
                nameExpr = (NameExpr) assignExpr.getTarget();
            } else if (assignExpr.getTarget() instanceof FieldAccessExpr && targetDecleration.isField()) {
                nameExpr = ((FieldAccessExpr) assignExpr.getTarget()).getFieldExpr();
            }

            boolean exclude = false;
            for (AssignExpr expr : exclusionList) {
                if (nameExpr != null && nameExpr.getName().equals(targetDecleration.getName())) {
                    if (expr.equals(assignExpr) && expr.getRange().equals(assignExpr.getRange())) {
                        exclude = true;
                    }
                }
            }
            if (nameExpr != null && nameExpr.getName().equals(targetDecleration.getName()) && !exclude) {
                exclusionList.add(assignExpr);
                return assignExpr;
            }
        }
        for (Node child : node.getChildrenNodes()) {
            AssignExpr res = findAssignExpressionToTarget(child, targetDecleration, exclusionList);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    public static MethodCallExpr findMethodCall(Node node, String methodName) {
        if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = (MethodCallExpr) node;
            if (methodCallExpr.getName().equals(methodName)) {
                return methodCallExpr;
            }
        }
        for (Node child : node.getChildrenNodes()) {
            MethodCallExpr res = findMethodCall(child, methodName);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    public static List<MethodCallExpr> getMethodCalls(Node node) {
        List<MethodCallExpr> methodCallExprList = new ArrayList<>();
        if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = (MethodCallExpr) node;
            methodCallExprList.add(methodCallExpr);
        }
        for (Node child : node.getChildrenNodes()) {
            methodCallExprList.addAll(getMethodCalls(child));
        }
        return methodCallExprList;
    }

    public static List<Node> getStringBuilderInitAndAppendMethodCalls(Node node, String stringBuilderName) {
        boolean checkChildren = true;
        List<Node> foundList = new ArrayList<>();
        if (node instanceof VariableDeclarator) {
            VariableDeclarator variableDeclarator = (VariableDeclarator) node;
            if (stringBuilderName.equals(variableDeclarator.getId().getName())) {
                foundList.add(variableDeclarator);
            }
        } else if (node instanceof AssignExpr) {
            AssignExpr assignExpr = (AssignExpr) node;
            if (assignExpr.getTarget() instanceof NameExpr) {
                NameExpr nameExpr = (NameExpr) assignExpr.getTarget();
                if (nameExpr.getName().equals(stringBuilderName)) {
                    foundList.add(assignExpr);
                }
            }
        } else if (node instanceof MethodCallExpr) {
            MethodCallExpr methodCallExpr = (MethodCallExpr) node;
            if (methodCallExpr.getName().equals("append") &&  methodCallExpr.getScope().isPresent()) {
                if (methodCallExpr.getScope().get() instanceof MethodCallExpr) {
                    //stacked appends; add to found foundList in reverse order
                    List<Expression> tmpList = new ArrayList<>();
                    tmpList.add((Expression) methodCallExpr.getArgs().getChildrenNodes().get(0));

                    MethodCallExpr methodCall = methodCallExpr;
                    do {
                        methodCall = (MethodCallExpr) methodCall.getScope().get();
                        tmpList.add((Expression) methodCall.getArgs().getChildrenNodes().get(0));
                    } while (methodCall.getScope().isPresent() && methodCall.getScope().get() instanceof MethodCallExpr);

                    //now check name; if it's what we're looking for add to foundList in reverse
                    //no need to check children as we've handled the chain
                    NameExpr nameExpr = (NameExpr) methodCall.getScope().get();
                    if (nameExpr.getName().equals(stringBuilderName)) {
                        Collections.reverse(tmpList);
                        foundList.addAll(tmpList);
                        checkChildren = false;
                    }
                } else {
                    NameExpr nameExpr = (NameExpr) methodCallExpr.getScope().get();
                    if (nameExpr.getName().equals(stringBuilderName)) {
                        Node n = methodCallExpr.getArgs();
                        foundList.add(n.getChildrenNodes().get(0));
                    }
                }
            }
        }

        if (checkChildren) {
            for (Node child : node.getChildrenNodes()) {
                foundList.addAll(getStringBuilderInitAndAppendMethodCalls(child, stringBuilderName));
            }
        }
        return foundList;
    }

    public static Node forceDemandVariableDeclaration(Node node, String name) {
        while (true) {
            Node declarator = demandVariableDeclaration(node, name);
            if (declarator == null) {
                Node parentNode = node.getParentNode();
                if (parentNode == null || parentNode == node) {
                    break; //couldn't find it even at highest level; return null
                } else {
                    node = parentNode;
                }
            } else {
                return declarator;
            }
        }
        return null;
    }

    public static Node demandVariableDeclaration(Node node, String name) {
        if (node instanceof VariableDeclarator) {
            VariableDeclarator variableDeclarator = (VariableDeclarator) node;
            if (variableDeclarator.getId().getName().equals(name)) {
                return variableDeclarator;
            }
        } else if (node instanceof VariableDeclaratorId) {
            VariableDeclaratorId variableDeclaratorId = (VariableDeclaratorId) node;
            if (variableDeclaratorId.getName().equals(name)) {
                return variableDeclaratorId;
            }
        } else if (node instanceof MethodDeclaration) {
            //check method parameters
            for (Parameter parameter : ((MethodDeclaration) node).getParameters()) {
                Node res = demandVariableDeclaration(parameter, name);
                if (res != null) {
                    return res;
                }
            }

        }
        for (Node child : node.getChildrenNodes()) {
            Node res = demandVariableDeclaration(child, name);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    public static ClassOrInterfaceDeclaration demandClassOrInterface(CompilationUnit compilationUnit, String qualifiedName) {
        Optional<TypeDeclaration<?>> res = findType(compilationUnit, qualifiedName);
        if (!res.isPresent()) {
            throw new IllegalStateException("No type named '" + qualifiedName + "'found");
        }
        if (!(res.get() instanceof ClassOrInterfaceDeclaration)) {
            throw new IllegalStateException("Type is not a class or an interface, it is " + res.get().getClass().getCanonicalName());
        }
        ClassOrInterfaceDeclaration cd = (ClassOrInterfaceDeclaration) res.get();
        return cd;
    }

    public static SwitchStmt findSwitch(Node node) {
        SwitchStmt res = findSwitchHelper(node);
        if (res == null) {
            throw new IllegalArgumentException();
        } else {
            return res;
        }
    }

    private static SwitchStmt findSwitchHelper(Node node) {
        if (node instanceof SwitchStmt) {
            return (SwitchStmt) node;
        }
        for (Node child : node.getChildrenNodes()) {
            SwitchStmt resChild = findSwitchHelper(child);
            if (resChild != null) {
                return resChild;
            }
        }
        return null;
    }

    private static <N> N findNodeOfGivenClassHelper(Node node, Class<N> clazz) {
        if (clazz.isInstance(node)) {
            return clazz.cast(node);
        }
        for (Node child : node.getChildrenNodes()) {
            N resChild = findNodeOfGivenClassHelper(child, clazz);
            if (resChild != null) {
                return resChild;
            }
        }
        return null;
    }

    public static <N> N findNodeOfGivenClass(Node node, Class<N> clazz) {
        N res = findNodeOfGivenClassHelper(node, clazz);
        if (res == null) {
            throw new IllegalArgumentException();
        } else {
            return res;
        }
    }

    public static <N> List<N> findAllNodesOfGivenClass(Node node, Class<N> clazz) {
        List<N> res = new LinkedList<>();
        findAllNodesOfGivenClassHelper(node, clazz, res);
        return res;
    }

    private static <N> void findAllNodesOfGivenClassHelper(Node node, Class<N> clazz, List<N> collector) {
        if (clazz.isInstance(node)) {
            collector.add(clazz.cast(node));
        }
        for (Node child : node.getChildrenNodes()) {
            findAllNodesOfGivenClassHelper(child, clazz, collector);
        }
    }

    public static ReturnStmt findReturnStmt(MethodDeclaration method) {
        return findNodeOfGivenClass(method, ReturnStmt.class);
    }

    public static <N extends Node> Optional<N> findAncestor(Node node, Class<N> clazz) {
        if (node.getParentNode() == null) {
            return Optional.empty();
        } else if (clazz.isInstance(node.getParentNode())) {
            return Optional.of(clazz.cast(node.getParentNode()));
        } else {
            return findAncestor(node.getParentNode(), clazz);
        }
    }

}
