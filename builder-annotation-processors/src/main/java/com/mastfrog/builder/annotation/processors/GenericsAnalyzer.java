/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.builder.annotation.processors;

import com.mastfrog.annotation.AnnotationUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.AbstractTypeVisitor9;

/**
 * Dissects the generics associated with an entire type, and extracts which
 * fields each is associated with.
 *
 * @author Tim Boudreau
 */
final class GenericsAnalyzer {

    private final TypeMirror targetType;
    private final ExecutableElement target;
    private final GenericsModel returnTypeModel;
    private final Map<String, GenericsModel> modelForParameter = new LinkedHashMap<>(12);

    GenericsAnalyzer(AnnotationUtils utils, ExecutableElement el) {
        this.target = el;
        if (el.getKind() == ElementKind.CONSTRUCTOR) {
            targetType = AnnotationUtils.enclosingTypeAsTypeMirror(el);
        } else {
            targetType = el.getReturnType();
        }

        TV tv = new TV();
        returnTypeModel = new GenericsModel();
        targetType.accept(tv, returnTypeModel);

        Map<String, GenericsModel> forParam = new LinkedHashMap<>();
        el.getParameters().forEach(ve -> {
            GenericsModel curr = new GenericsModel();
            TV v = new TV();
            ve.asType().accept(v, curr);
            modelForParameter.put(ve.getSimpleName().toString(), curr);
        });
    }

    Set<TypeModel> all() {
        Set<TypeModel> all = new LinkedHashSet<>(returnTypeModel.all);
        modelForParameter.values().forEach(v -> {
            all.addAll(v.all);
        });
        return all;
    }

    public String nameWithBound(String genericType) {
        TypeModel target = null;
        for (TypeModel tm : all()) {
            if (tm.toString().equals(genericType)) {
                target = tm;
                break;
            }
        }
        if (target == null) {
            return genericType;
        }
        StringBuilder result = new StringBuilder(genericType);
        for (Map.Entry<ModelPair, Set<Relationship>> e : returnTypeModel.relationships.entrySet()) {
            ModelPair pair = e.getKey();
            if (pair.headMatches(target)) {
                if (e.getValue().contains(Relationship.UPPER_BOUND)) {
                    TypeModel bnd = pair.b;
                    if (bnd.kind() == TypeKind.NONE || bnd.kind() == TypeKind.NULL) {
                        continue;
                    }
                    String what = bnd.toString();
                    if (what.startsWith("java.lang.Object&")) {
                        what = what.substring("java.lang.Object&".length());
                    }
                    what = what.replaceAll("&", " & ");
                    if ("java.lang.Object".equals(what)) {
                        continue;
                    }
                    result.append(" extends ").append(what);
                    break;
                } else if (e.getValue().contains(Relationship.LOWER_BOUND)) {
                    TypeModel bnd = pair.b;
                    if (bnd.kind() == TypeKind.NONE || bnd.kind() == TypeKind.NULL) {
                        continue;
                    }
                    String what = bnd.toString();
                    if ("java.lang.Object".equals(what)) {
                        continue;
                    }
                    if (what.startsWith("java.lang.Object&")) {
                        what = what.substring("java.lang.Object&".length());
                    }
                    what = what.replaceAll("&", " & ");
                    result.append(" super ").append(what);
                    break;
                }
            }
        }
        return result.toString();
    }

    private List<String> sort(Collection<String> generics) {
        if (generics.size() <= 1) {
            return generics instanceof List<?> ? (List<String>) generics : new ArrayList<>(generics);
        }
        List<String> ordered = new ArrayList<>(generics.size());
        for (TypeModel tm : all()) {
            if (generics.contains(tm.toString())) {
                ordered.add(tm.toString());
            }
        }
        if (ordered.size() != generics.size()) {
            throw new IllegalStateException("Some generics not found in " + generics);
        }
        return ordered;
    }

    private static Set<String> collapse(Collection<? extends Supplier<String>> names) {
        Set<String> result = new LinkedHashSet<>(names.size());
        names.forEach(nm -> result.add(nm.get()));
        return result;
    }

    public List<String> genericNamesRequiredFor(Collection<? extends Supplier<String>> names) {
        Set<String> result = new LinkedHashSet<>();
        for (String nm : collapse(names)) {
            for (TypeModel tm : genericsRequiredFor(nm)) {
                result.add(tm.toString());
            }
        }
        return sort(result);
    }

    public Set<TypeModel> genericsRequiredFor(String param) {
        GenericsModel m = modelForParameter.get(param);
        if (m == null) {
            return Collections.emptySet();
        }
        Set<TypeModel> generics = returnTypeModel.allOfKind(TypeKind.TYPEVAR);
        return m.intersection(generics);
    }

    static class GenericsModel {

        private final Set<TypeModel> all = new LinkedHashSet<>();
        private final Map<TypeModel, List<TypeModel>> children = new LinkedHashMap<>();
        private final Map<ModelPair, Set<Relationship>> relationships = new LinkedHashMap<>();
        private final Set<TypeKind> kinds = EnumSet.noneOf(TypeKind.class);
        TypeModel root;
        TypeModel curr;

        GenericsModel visit(TypeMirror mir, Relationship rel, Runnable run) {
            TypeModel old = curr;
            if (old != null && old.mir == mir) {
                return this;
            }
            kinds.add(mir.getKind());
            curr = new TypeModel(mir);
            all.add(curr);
            try {
                if (old != null) {
                    List<TypeModel> kids = children.computeIfAbsent(old, o -> new ArrayList<>(5));
                    kids.add(curr);
                    if (rel != Relationship.NONE) {
                        ModelPair pair = new ModelPair(old, curr);
                        relationships.computeIfAbsent(pair, p -> {
                            return EnumSet.noneOf(Relationship.class);
                        }).add(rel);
                    }
                }
                run.run();
            } finally {
                if (old == null) {
                    root = curr;
                }
                curr = old;
            }
            return this;
        }

        public Set<TypeModel> intersection(Set<TypeModel> other) {
            Set<TypeModel> result = new LinkedHashSet<>(this.all);
            result.retainAll(other);
            return result;
        }

        Set<TypeModel> allOfKind(TypeKind k) {
            Set<TypeModel> result = new LinkedHashSet<>();
            for (TypeModel tm : this.all) {
                if (k == tm.kind()) {
                    result.add(tm);
                }
            }
            return result;
        }

        public StringBuilder stringify(TypeModel parent, int depth, TypeModel mdl, StringBuilder into) {
            char[] c = new char[2 * depth];
            Arrays.fill(c, ' ');
            for (int i = 0; i < c.length; i += 2) {
                c[i] = '|';
            }
            into.append(c);
            into.append(mdl);
            into.append(' ').append(mdl.kind().name()).append(' ').append(mdl.getClass().getSimpleName());
            if (parent != null) {
                ModelPair pair = new ModelPair(parent, mdl);
                Set<Relationship> rels = this.relationships.get(pair);
                if (rels != null && !rels.isEmpty()) {
                    rels.forEach(r -> into.append(' ').append(r.name()));
                }
            }
            into.append('\n');
            List<TypeModel> l = children.get(mdl);
            if (l != null) {
                l.forEach(m -> stringify(mdl, depth + 1, m, into));
            }
            return into;
        }

        public String toString() {
            if (root == null) {
                return "-none-";
            }
            return stringify(null, 0, root, new StringBuilder("Kinds: " + kinds + "\n")).toString();
        }
    }

    static class TypeModel {

        final TypeMirror mir;

        public TypeModel(TypeMirror mir) {
            this.mir = mir;
        }

        @Override
        public String toString() {
            String result = mir.toString();
            if (result.startsWith("java.lang.Object&")) {
                return result.substring("java.lang.Object&".length());
            }
            return result;
        }

        public TypeKind kind() {
            return mir.getKind();
        }

        public boolean equals(Object o) {
            if (o == this) {
                return false;
            } else if (o == null || o.getClass() != TypeModel.class) {
                return false;
            }
            TypeModel tm = (TypeModel) o;
            if (tm.mir == mir) {
                return true;
            }
            return tm.mir.toString().equals(mir.toString());
        }

        public int hashCode() {
            return mir.toString().hashCode();
        }
    }

    enum Relationship {
        UPPER_BOUND,
        LOWER_BOUND,
        CHILD,
        ALTERNATIVE,
        BOUND,
        ARRAY_MEMBER,
        EXTENDS,
        SUPER,
        EXE_TYPE_VAR,
        EXE_RETURN,
        EXE_PARAM,
        EXE_RECEIVER,
        EXE_THROWN,
        NONE
    }

    static class ModelPair {

        final TypeModel a;
        final TypeModel b;

        public ModelPair(TypeModel a, TypeModel b) {
            this.a = a;
            this.b = b;
        }

        public boolean headMatches(TypeModel target) {
            return target.toString().equals(a.toString());
        }

        boolean contains(TypeMirror tm) {
            return a.mir.equals(tm) || b.mir.equals(tm);
        }

        boolean contains(TypeModel tm) {
            return a.equals(tm) || b.equals(tm);
        }

        public String toString() {
            return a + "/" + b;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 83 * hash + Objects.hashCode(this.a);
            hash = 83 * hash + Objects.hashCode(this.b);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ModelPair other = (ModelPair) obj;
            if (!Objects.equals(this.a, other.a)) {
                return false;
            }
            return Objects.equals(this.b, other.b);
        }
    }

    static class TV extends AbstractTypeVisitor9<GenericsModel, GenericsModel> {

        private GenericsModel handle(TypeMirror tm, Relationship rel, GenericsModel model) {
            return handle(tm, rel, model, () -> {
            });
        }

        Relationship currRel;

        private void withRelationship(Relationship r, Runnable run) {
            Relationship old = currRel;
            currRel = r;
            try {
                run.run();
            } finally {
                currRel = old;
            }
        }

        private GenericsModel handle(TypeMirror tm, Relationship rel, GenericsModel model, Runnable run) {
            if (currRel != null && rel == Relationship.NONE) {
                rel = currRel;
            }
            return model.visit(tm, rel, run);
        }

        @Override
        public GenericsModel visitIntersection(IntersectionType it, GenericsModel p) {
            return handle(it, Relationship.NONE, p, () -> {
                withRelationship(Relationship.BOUND, () -> {
                    for (TypeMirror m : it.getBounds()) {
                        m.accept(this, p);
                    }
                });
            });
        }

        @Override
        public GenericsModel visitUnion(UnionType ut, GenericsModel p) {
            return handle(ut, Relationship.NONE, p, () -> {
                withRelationship(Relationship.BOUND, () -> {
                    for (TypeMirror m : ut.getAlternatives()) {
                        m.accept(this, p);
                    }
                });
            });
        }

        @Override
        public GenericsModel visitPrimitive(PrimitiveType t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> t.accept(this, p));
        }

        @Override
        public GenericsModel visitNull(NullType t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> t.accept(this, p));
        }

        @Override
        public GenericsModel visitArray(ArrayType t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> {
                withRelationship(Relationship.ARRAY_MEMBER, () -> {
                    t.getComponentType().accept(this, p);
                });
            });
        }

        @Override
        public GenericsModel visitDeclared(DeclaredType t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> {
                withRelationship(Relationship.CHILD, () -> {
                    for (TypeMirror m : t.getTypeArguments()) {
                        m.accept(this, p);
                    }
                });
            });
        }

        @Override
        public GenericsModel visitError(ErrorType t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> {
                withRelationship(Relationship.CHILD, () -> {
                    for (TypeMirror m : t.getTypeArguments()) {
                        m.accept(this, p);
                    }
                });
            });
        }

        @Override
        public GenericsModel visitTypeVariable(TypeVariable t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> {
                withRelationship(Relationship.LOWER_BOUND, () -> {
                    t.getLowerBound().accept(this, p);
                });
                withRelationship(Relationship.UPPER_BOUND, () -> {
                    t.getUpperBound().accept(this, p);
                });
            });
        }

        @Override
        public GenericsModel visitWildcard(WildcardType t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> {
                if (t.getSuperBound() != null) {
                    withRelationship(Relationship.SUPER, () -> {
                        t.getSuperBound().accept(this, p);
                    });
                }
                if (t.getExtendsBound() != null) {
                    withRelationship(Relationship.EXTENDS, () -> {
                        t.getExtendsBound().accept(this, p);
                    });
                }
            });
        }

        @Override
        public GenericsModel visitExecutable(ExecutableType t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> {
                withRelationship(Relationship.EXE_PARAM, () -> {
                    t.getParameterTypes().forEach(th -> th.accept(this, p));
                });
                withRelationship(Relationship.EXE_RETURN, () -> {
                    t.getReturnType().accept(this, p);
                });
                if (t.getReceiverType() != null) {
                    withRelationship(Relationship.EXE_RECEIVER, () -> {
                        t.getReceiverType().accept(this, p);
                    });
                }
                withRelationship(Relationship.EXE_THROWN, () -> {
                    t.getThrownTypes().forEach(th -> th.accept(this, p));
                });
                withRelationship(Relationship.EXE_TYPE_VAR, () -> {
                    t.getTypeVariables().forEach(th -> th.accept(this, p));
                });

            });
        }

        @Override
        public GenericsModel visitNoType(NoType t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> t.accept(this, p));
        }

        @Override
        public GenericsModel visitUnknown(TypeMirror t, GenericsModel p) {
            return handle(t, Relationship.NONE, p, () -> t.accept(this, p));
        }
    }
}
