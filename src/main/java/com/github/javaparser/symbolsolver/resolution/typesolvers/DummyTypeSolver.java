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

package com.github.javaparser.symbolsolver.resolution.typesolvers;

import com.github.javaparser.symbolsolver.model.declarations.TypeDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.SymbolReference;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DummyTypeSolver implements TypeSolver {

    private TypeSolver parent;
    private Map<String, TypeDeclaration> declarationMap = new HashMap<>();

    @Override
    public TypeSolver getParent() {
        return parent;
    }

    @Override
    public void setParent(TypeSolver parent) {
        this.parent = parent;
    }

    public void addDeclaration(String name, TypeDeclaration typeDeclaration) {
        this.declarationMap.put(name, typeDeclaration);
    }

    @Override
    public SymbolReference<TypeDeclaration> tryToSolveType(String name) {
        if (declarationMap.containsKey(name)) {
            return SymbolReference.solved(declarationMap.get(name));
        } else {
            return SymbolReference.unsolved(TypeDeclaration.class);
        }
    }

    @Override
    public List<String> getAllDefinedClassNames() {
        return null;
    }
}
