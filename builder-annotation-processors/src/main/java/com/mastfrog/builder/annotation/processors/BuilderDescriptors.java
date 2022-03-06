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
import static com.mastfrog.annotation.AnnotationUtils.capitalize;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.MethodBuilder;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Generated;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

/**
 *
 * @author Tim Boudreau
 */
final class BuilderDescriptors {

    private final Map<Element, BuilderDescriptor> descs = new HashMap<>();
    private final AnnotationUtils utils;

    BuilderDescriptors(AnnotationUtils utils) {
        this.utils = utils;
    }

    public void add(Element e, Set<BuilderStyles> styles, Consumer<BuilderDescriptor> c) {
        c.accept(descs.computeIfAbsent(e, e1 -> new BuilderDescriptor(e, styles)));
    }

    public void generate() throws IOException {
        Filer filer = utils.processingEnv().getFiler();
        for (Map.Entry<Element, BuilderDescriptor> e : descs.entrySet()) {
            ClassBuilder cb = e.getValue().generate();
            JavaFileObject src = filer.createSourceFile(cb.fqn(), e.getValue().elements());
            try ( OutputStream out = src.openOutputStream()) {
                out.write(cb.toString().getBytes(UTF_8));
            }
            System.out.println(src);
        }
    }
    static final String[] PRIMITIVE_TYPES = {
        Byte.TYPE.getName(),
        Byte.class.getName(),
        Short.TYPE.getName(),
        Short.class.getName(),
        Integer.TYPE.getName(),
        Integer.class.getName(),
        Long.TYPE.getName(),
        Long.class.getName(),
        Character.TYPE.getName(),
        Character.class.getName(),
        Float.TYPE.getName(),
        Float.class.getName(),
        Double.TYPE.getName(),
        Double.class.getName(),
        Boolean.TYPE.getName(),
        Boolean.class.getName(),
        Void.TYPE.getName(),
        Void.class.getName(),};

    static final String[] BOOLEAN_TYPES = {
        Boolean.TYPE.getName(),
        Boolean.class.getName()
    };

    public class BuilderDescriptor {

        private static final String SET_PRIM_FIELDS = "__setPrimitiveFields";

        private final Element origin;
        private TypeElement instanceType;
        private final Map<VariableElement, FieldDescriptor> paramForVar = new LinkedHashMap<>();
        private String builderName;
        private String targetTypeName;
        private long allPrimitivesMask;
        private final Set<BuilderStyles> styles;

        BuilderDescriptor(Element e, Set<BuilderStyles> styles) {
            this.origin = e;
            this.styles = styles;
            String nm;
            switch (e.getKind()) {
                case METHOD:
                    ExecutableElement ex = (ExecutableElement) e;
                    nm = AnnotationUtils.simpleName(ex.getReturnType());
                    builderName = nm + "Builder";
                    break;
                default:
                    nm = AnnotationUtils.enclosingType(e).getSimpleName().toString();
                    String[] parts = nm.split("[\\.\\$]");
                    if (parts.length > 0) {
                        builderName = parts[parts.length - 1] + "Builder";
                    } else {
                        builderName = nm + "Builder";
                    }
            }
            targetTypeName = nm;
        }

        private <B extends ClassBuilder<C>, C> B addGeneratedAnnotation(B cb) {
            cb.importing(Generated.class);
            cb.annotatedWith(Generated.class.getSimpleName(), ab -> {
                ab.addArgument("value", BuilderAnnotationProcessor.class.getName());
            });
            return cb;
        }

        private void indexPrimitives() {
            List<FieldDescriptor> fds = new ArrayList<>(paramForVar.values());
            Collections.sort(fds);
            allPrimitivesMask = 0;
            int cursor = 0;
            for (int i = 0; i < fds.size(); i++) {
                if (!fds.get(i).optional) {
                    fds.get(i).primitiveIndex = cursor;
                    allPrimitivesMask |= 1L << cursor;
                    cursor++;
                }
            }
        }

        Element[] elements() {
            return allElements().toArray(new Element[0]);
        }

        Collection<Element> allElements() {
            Set<Element> all = new LinkedHashSet<>();
            all.add(origin);
            paramForVar.forEach((ve, d) -> {
                all.add(ve);
            });
            return all;
        }

        void onInstanceOf(TypeElement type) {
            this.instanceType = type;
        }

        void handleOneParameter(String fieldName, boolean optional, VariableElement param, Set<ConstraintGenerator> constraints) {
            FieldDescriptor fv = new FieldDescriptor(param, optional, fieldName, constraints);
            paramForVar.put(param, fv);
        }

        private boolean containsName(String nm) {
            for (FieldDescriptor fd : paramForVar.values()) {
                if (nm.equals(fd.fieldName)) {
                    return true;
                }
            }
            return false;
        }

        private String uniquify(String s) {
            String test = s;
            while (containsName(test)) {
                test = "_" + test;
            }
            return test;
        }

        private String packageName() {
            String result = utils.packageName(origin);
            int ix = result.indexOf('(');
            if (ix > 0) {
                result = result.substring(0, ix);
            }
            return result;
        }

        private void generateIsSetMethod(ClassBuilder<String> cb) {
            boolean needed = false;
            for (FieldDescriptor v : paramForVar.values()) {
                if (v.optional && !v.constraints.isEmpty()) {
                    needed = true;
                    break;
                }
            }
            if (needed) {
                cb.method("_isSet").withModifier(Modifier.PRIVATE)
                        .returning("boolean")
                        .addArgument("String", "__fieldName")
                        .body(bb -> {
                            bb.switchingOn("__fieldName", sw -> {
                                for (FieldDescriptor v : paramForVar.values()) {
                                    sw.inCase(v.fieldName, bl -> {
                                        int ix = v.primitiveIndex();
                                        if (ix != -1) {
                                            bl.returning("( " + SET_PRIM_FIELDS + " & 1L << " + ix + ") != 0");
                                        } else {
                                            bl.returning(v.fieldName + " != null");
                                        }
                                    });
                                    sw.inDefaultCase(dc -> {
                                        dc.andThrow(nb -> {
                                            nb.withStringConcatentationArgument("Unknown field '")
                                                    .appendExpression("__fieldName").append('\'')
                                                    .endConcatenation().ofType("AssertionError");
                                        });
                                    });
                                }
                            });
                        });
            }
        }

        private List<FieldDescriptor> sorted(Collection<? extends FieldDescriptor> c) {
            List<FieldDescriptor> result = new ArrayList<>(c);
            Collections.sort(result);
            return result;
        }

        private Set<FieldDescriptor> omitting(FieldDescriptor one, Set<FieldDescriptor> all) {
            Set<FieldDescriptor> result = new HashSet<>(all);
            result.remove(one);
            return result;
        }

        private Set<FieldDescriptor> including(FieldDescriptor one, Set<FieldDescriptor> all) {
            Set<FieldDescriptor> result = new HashSet<>(all);
            result.add(one);
            return result;
        }

        private Set<FieldDescriptor> combine(Collection<? extends FieldDescriptor> a, Collection<? extends FieldDescriptor> b) {
            Set<FieldDescriptor> result = new HashSet<>(a);
            result.addAll(b);
            return result;
        }

        ClassBuilder<String> generate() throws IOException {
            return generateFlat();
//            if (styles.contains(BuilderStyles.FLAT)) {
//                return generateFlat();
//            } else {
//                return generateCartesian();
//            }
        }
/*
        ClassBuilder<String> generateCartesian() throws IOException {
            Set<FieldDescriptor> requiredFields = requiredFields();
            Set<FieldDescriptor> optionalFields = optionalFields();
            Set<FieldDescriptor> used = Collections.emptySet();
            OneProductBuilder opb = new OneProductBuilder(null, used, requiredFields, optionalFields);
            cartBuilders.put(builderName, opb);
            return opb.build().parent;
        }

        private Map<String, OneProductBuilder> cartBuilders = new HashMap<>();

        private String oneBuilderName(Collection<? extends FieldDescriptor> used, Collection<? extends FieldDescriptor> unused) {
            if (used.isEmpty()) {
                return builderName;
            }
            StringBuilder sb = new StringBuilder(builderName).append("With");
            for (FieldDescriptor fd : sorted(used)) {
                sb.append(capitalize(fd.fieldName));
            }
            StringBuilder sb2 = new StringBuilder(builderName).append("Sans");
            for (FieldDescriptor fd : sorted(unused)) {
                sb2.append(capitalize(fd.fieldName));
            }
            if (sb2.length() < sb.length()) {
                return sb2.toString();
            }
            return sb.toString();
        }

        private final Map<String, OneProductBuilder> cartesians = new HashMap<>();

        private OneProductBuilder subBuilder(FieldDescriptor applying,
                ClassBuilder<String> parent,
                Set<FieldDescriptor> used,
                Set<FieldDescriptor> required, Set<FieldDescriptor> optional) {

            String nm = oneBuilderName(used, required);
            OneProductBuilder opb = cartesians.get(nm);
            if (opb == null) {
                opb = new OneProductBuilder(parent, including(applying, used),
                        omitting(applying, required), optional);
                cartesians.put(nm, opb);
                return opb;
            }
            return null;
        }

        private ClassBuilder<String> parentBuilder(ClassBuilder<String> parent) {
            if (parent == null) {
                parent = addGeneratedAnnotation(ClassBuilder.forPackage(packageName())
                        .named(builderName))
                        .withModifier(Modifier.FINAL, Modifier.PUBLIC)
                        .autoToString()
                        .constructor(con -> {
                            con.setModifier(Modifier.PUBLIC)
                                    .body().endBlock();
                        });
            }
            return parent;
        }

        class OneProductBuilder {

            private final ClassBuilder<String> parent;
            private final ClassBuilder<?> target;
            private final Set<FieldDescriptor> used;
            private final Set<FieldDescriptor> unused;
            private final Set<FieldDescriptor> optional;
            private String nm;

            OneProductBuilder(ClassBuilder<String> parent, Set<FieldDescriptor> used, Set<FieldDescriptor> required, Set<FieldDescriptor> optional) {
                this.parent = parentBuilder(parent);
                this.used = used;
                this.unused = required;
                this.optional = optional;
                nm = oneBuilderName(used, required);
                if (parent == null) {
                    target = this.parent;
                } else {
                    target = addGeneratedAnnotation(parent
                            .innerClass(nm)
                            .withModifier(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                            .autoToString());
                }
            }

            String name() {
                return nm;
            }

            private void generateFields() {
                for (FieldDescriptor fd : used) {
                    target.field(fd.fieldName)
                            .withModifier(Modifier.PRIVATE, Modifier.FINAL)
                            .ofType(fd.typeName());
                }
                for (FieldDescriptor fd : optional) {
                    target.field(fd.fieldName)
                            .withModifier(Modifier.PRIVATE)
                            .ofType(fd.typeName());
                }
            }

            private List<FieldDescriptor> assignedFields() {
                List<FieldDescriptor> result = new ArrayList<>(used);
                result.addAll(optional);
                Collections.sort(result);
                return result;
            }

            private void generateConstructor() {
                target.constructor(cb -> {
                    List<FieldDescriptor> existing = assignedFields();
                    for (FieldDescriptor fd : existing) {
                        cb.addArgument(fd.typeName(), fd.fieldName);
                    }
                    cb.body(bb -> {
                        for (FieldDescriptor fd : existing) {
                            if (!fd.optional && !fd.isPrimitive()) {
                                bb.ifNull(fd.fieldName)
                                        .andThrow(nb -> {
                                            nb.withStringConcatentationArgument(fd.fieldName)
                                                    .append(" may not be null.")
                                                    .endConcatenation()
                                                    .ofType("IllegalArgumentException");
                                        });
                            }
                        }

                        for (FieldDescriptor fd : existing) {
                            bb.assign("this." + fd.fieldName).toExpression(
                                    fd.fieldName);
                        }
                    });
                });
            }

            private void generateBuildMethod() {
                if (unused.size() == 1) {
                    FieldDescriptor last = unused.iterator().next();
                    String typeToBuild = BuilderDescriptor.this.targetTypeName;
                    target.method("buildWith" + capitalize(last.fieldName))
                            .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                            .returning(typeToBuild)
                            .body(bb -> {
                                bb.returningNew(nb -> {
                                    for (FieldDescriptor fd : paramForVar.values()) {
                                        if (fd != last) {
                                            nb.withArgumentFromField(fd.fieldName).ofThis();
                                        } else {
                                            nb.withArgument(fd.fieldName);
                                        }
                                    }
                                    nb.ofType(typeToBuild);
                                });
                            });
                }
            }

            private void generateOptionalSetters() {
                for (FieldDescriptor fd : sorted(optional)) {
                    fd.generate(target);
                }
            }

            private void generateRequiredSetters() {
                if (unused.size() == 1) {
                    System.out.println("  no req setters for " + nm);
                    return;
                }
                for (FieldDescriptor fd : sorted(unused)) {
                    List<FieldDescriptor> args = sorted(including(fd, used));
                    String builderName = oneBuilderName(args, omitting(fd, unused));
                    target.method("with" + capitalize(fd.fieldName))
                            .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                            .addArgument(fd.typeName(), fd.fieldName)
                            .docComment(fd.setterJavadoc())
                            .returning(builderName)
                            .body(bb -> {
                                OneProductBuilder opb
                                        = subBuilder(fd, parent,
                                                used, unused, optional);
                                bb.returningNew(nb -> {
                                    for (FieldDescriptor a : args) {
                                        if (a != fd) {
                                            nb.withArgumentFromField(a.fieldName).ofThis();
                                        } else {
                                            nb.withArgument(a.fieldName);
                                        }
                                    }
                                    nb.ofType(builderName);
                                });
                                if (opb != null) {
                                    System.out.println("CREATE SUB " + opb.name());
                                    opb.build();
                                }
                            });
                }
            }

            public OneProductBuilder build() {
                System.out.println("building " + nm);
                generateConstructor();
                generateFields();
                generateOptionalSetters();
                generateRequiredSetters();
                generateBuildMethod();
                return this;
            }
        }
        */

        Set<FieldDescriptor> requiredFields() {
            Set<FieldDescriptor> result = new LinkedHashSet<>();
            for (FieldDescriptor fv : paramForVar.values()) {
                if (!fv.optional) {
                    result.add(fv);
                }
            }
            return result;
        }

        Set<FieldDescriptor> optionalFields() {
            Set<FieldDescriptor> result = new LinkedHashSet<>();
            for (FieldDescriptor fv : paramForVar.values()) {
                if (fv.optional) {
                    result.add(fv);
                }
            }
            return result;
        }

        ClassBuilder<String> generateFlat() throws IOException {
            String pn = packageName();

            ClassBuilder<String> cb = addGeneratedAnnotation(ClassBuilder.forPackage(
                    pn).named(builderName)
                    .docComment("Generated builder from annotations on " + origin)
                    .withModifier(Modifier.FINAL).withModifier(Modifier.PUBLIC)
                    .autoToString())
                    .generateDebugLogCode();

            indexPrimitives();
            generateIsSetMethod(cb);
            for (FieldDescriptor fd : paramForVar.values()) {
                for (ConstraintGenerator g : fd.constraints) {
                    g.decorateClass(cb);
                }
            }
            if (allPrimitivesMask != 0) {
                cb.field(SET_PRIM_FIELDS)
                        .withModifier(Modifier.PRIVATE)
                        .ofType("long");
            }

            for (Map.Entry<VariableElement, FieldDescriptor> e : paramForVar.entrySet()) {
                e.getValue().generate(cb);
            }
            ClassBuilder.MethodBuilder<ClassBuilder<String>> mb = cb.method("build")
                    .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                    .returning(targetTypeName);
            if (instanceType != null) {
                String targetParam = uniquify("target");
                mb.addArgument(instanceType.getSimpleName().toString(), targetParam);
                ExecutableElement ex = (ExecutableElement) origin;
                String methodName = ex.getSimpleName().toString();
                mb.body(bb -> {
                    generateUnsetCheck(bb);
                    bb.returningInvocationOf(methodName, iv -> {
                        for (Map.Entry<VariableElement, FieldDescriptor> e : paramForVar.entrySet()) {
                            e.getValue().generate(iv);
                        }
                        iv.on(targetParam);
                    });
                });
            } else {
                mb.body(bb -> {
                    generateUnsetCheck(bb);
                    bb.returningNew(nb -> {
                        for (Map.Entry<VariableElement, FieldDescriptor> e : paramForVar.entrySet()) {
                            nb.withArgumentFromField(e.getValue().fieldName).ofThis();
                        }
                        nb.ofType(targetTypeName);
                    });
                });
            }
            return cb;
        }

        boolean hasConstraintGenerators() {
            boolean result = false;
            for (FieldDescriptor v : paramForVar.values()) {
                result = !v.constraints.isEmpty();
                if (result) {
                    break;
                }
            }
            return result;
        }

        public void generateUnsetCheck(BlockBuilderBase<?, ?, ?> bb) {
            boolean hcgs = hasConstraintGenerators();
            if (this.allPrimitivesMask != 0 || hcgs) {
                ClassBuilder.IfBuilder<?> iff = bb.iff().booleanExpression("(this." + SET_PRIM_FIELDS + " & " + this.allPrimitivesMask + "L) != " + this.allPrimitivesMask + "L");
                String probName = uniquify("problems");
                BlockBuilderBase<?, ?, ?> probsOwnerBlock = iff;
                if (!hcgs) {
                    probsOwnerBlock = bb;
                }
                probsOwnerBlock.declare(probName).initializedWithNew().ofType(ArrayList.class.getName() + "<String>").as(List.class.getName() + "<String>");

                for (FieldDescriptor fd : paramForVar.values()) {
                    fd.generateValidityCheck(iff, probName);
                    fd.generateConstraints(probName, probsOwnerBlock);
                }

                ClassBuilder.IfBuilder<?> haveProblsBlock = probsOwnerBlock.iff().invoke("isEmpty").on(probName).isFalse().endCondition();

                String sb = uniquify("message");
                haveProblsBlock.declare(sb).initializedWithNew().ofType(StringBuilder.class.getName()).as(StringBuilder.class.getName());
                String currProb = uniquify("currentProblem");
                haveProblsBlock.simpleLoop("String", currProb, slb -> {
                    slb.over(probName, sbb -> {
                        sbb.iff().invoke("length").on(sb).notEquals().literal(0)
                                .endCondition().invoke("append").withStringLiteral("; ")
                                .on(sb).endIf();
                        sbb.invoke("append").withArgument(currProb).on(sb);
                    });
                });
                haveProblsBlock.invoke("append").withArgument("this")
                        .onInvocationOf("append").withStringLiteral(" in ").on(sb);

                haveProblsBlock.andThrow().withArgumentFromInvoking("toString").on(sb).ofType("IllegalStateException");
                iff.endIf();
            }
        }

        class FieldDescriptor implements Comparable<FieldDescriptor> {

            private final Set<ConstraintGenerator> constraints;
            private final VariableElement var;
            private final boolean optional;
            private String fieldName;
            private int primitiveIndex = -1;

            public FieldDescriptor(VariableElement var, boolean optional, String fieldName, Set<ConstraintGenerator> constraints) {
                this.var = var;
                this.optional = optional;
                this.fieldName = fieldName;
                this.constraints = constraints;
            }

            private int primitiveIndex() {
                if (primitiveIndex == -1) {
                    indexPrimitives();
                }
                return primitiveIndex;
            }

            public FieldDescriptor withFieldName(String fieldName) {
                this.fieldName = fieldName;
                return this;
            }

            private String typeName() {
                String tp = var.asType().toString();
                if (isPrimitive() && optional) {
                    switch (tp) {
                        case "char":
                            return "Character";
                        default:
                            return capitalize(tp);
                    }
                }
                return tp;
            }

            <C> void generateValidityCheck(BlockBuilderBase<C, ?, ?> bb, String problemCollectionName) {
                int pi = primitiveIndex();
                if (pi != -1) {
                    bb.iff().booleanExpression("(this." + SET_PRIM_FIELDS + " & "
                            + (1L << pi) + "L) == 0")
                            .invoke("add").withStringLiteral(fieldName + " was not set").on(problemCollectionName)
                            .endIf();
                }
            }

            String setterJavadoc() {
                StringBuilder sb = new StringBuilder("Sets the ")
                        .append(optional ? "" : "<b>required</b> ")
                        .append("parameter ")
                        .append(fieldName).append(" of ")
                        .append(" type <code>")
                        .append(AnnotationUtils.simpleName(typeName()))
                        .append("</code>.");
                if (!constraints.isEmpty()) {
                    List<String> bps = new ArrayList<>();
                    for (ConstraintGenerator c : constraints) {
                        c.contributeDocComments(bps::add);
                    }
                    if (!bps.isEmpty()) {
                        sb.append("\n<h2>").append("Constrained by</h2>\n")
                                .append("<ul>\n");
                        for (String bp : bps) {
                            sb.append("  <li>").append(bp).append("</li>\n");
                        }
                        sb.append("</ul>\n");
                    }
                }
                return sb.toString();
            }

            <C> void generate(ClassBuilder<C> cb) {
                String tn = typeName();
                cb.field(fieldName).withModifier(Modifier.PRIVATE).ofType(tn);
                cb.method("with" + capitalize(fieldName))
                        .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                        .addArgument(tn, fieldName)
                        .docComment(setterJavadoc())
                        .returning(cb.className())
                        .body(bb -> {
                            boolean prim = isPrimitive();
                            if (!prim && !optional) {
                                bb.ifNull(fieldName, ib -> {
                                    ib.andThrow(nb -> {
                                        nb.withArgument(sc -> {
                                            sc.stringConcatenation().expression(fieldName)
                                                    .append(" may not be null")
                                                    .endConcatenation();

                                        }).ofType(IllegalArgumentException.class.getName());
                                    });
                                });
                            }
                            if (prim) {
                                bb.statement("this." + SET_PRIM_FIELDS + " |= " + (1L << primitiveIndex()) + "L");
                            }
                            bb.assign("this." + fieldName).toExpression(fieldName)
                                    .returningThis();
                        });
            }

            void generateConstraints(String problemsVar, BlockBuilderBase<?, ?, ?> into) {
                for (ConstraintGenerator gen : this.constraints) {
                    gen.generate(fieldName, problemsVar, utils, into);
                }
            }

            <C> void generate(InvocationBuilder<C> ib) {
                ib.withArgumentFromField(fieldName).of("this");
            }

            boolean isPrimitive() {
                TypeMirror type = var.asType();
                for (String tp : PRIMITIVE_TYPES) {
                    if (utils.isAssignable(type, tp)) {
                        return true;
                    }
                }
                return false;
            }

            boolean isString() {
                return utils.isAssignable(var.asType(), String.class.getName());
            }

            boolean isCollection() {
                return utils.isAssignable(var.asType(), Collection.class.getName());
            }

            @Override
            public int compareTo(FieldDescriptor o) {
                return fieldName.compareTo(o.fieldName);
            }
        }
    }
}
