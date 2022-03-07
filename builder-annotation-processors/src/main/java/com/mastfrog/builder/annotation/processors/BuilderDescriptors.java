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
import static com.mastfrog.annotation.AnnotationUtils.simpleName;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.SwitchBuilder;
import java.io.IOException;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

    final Map<Element, BuilderDescriptor> descs = new HashMap<>();
    final AnnotationUtils utils;

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

    static <B extends ClassBuilder<C>, C> B addGeneratedAnnotation(B cb) {
        cb.importing(Generated.class);
        cb.annotatedWith(Generated.class.getSimpleName(), ab -> {
            ab.addArgument("value", BuilderAnnotationProcessor.class.getName());
        });
        return cb;
    }

    public class BuilderDescriptor {

        private static final String SET_PRIM_FIELDS = "__setPrimitiveFields";

        final Element origin;
        TypeElement instanceType;
        final Map<VariableElement, FieldDescriptor> paramForVar = new LinkedHashMap<>();
        String builderName;
        String targetTypeName;
        long allPrimitivesMask;
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

        AnnotationUtils utils() {
            return BuilderDescriptors.this.utils;
        }

        private void indexPrimitives() {
            List<FieldDescriptor> fds = new ArrayList<>(paramForVar.values());
            Collections.sort(fds);
            allPrimitivesMask = 0;
            int cursor = 0;
            for (int i = 0; i < fds.size(); i++) {
                if (!fds.get(i).optional && fds.get(i).isPrimitive()) {
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

        boolean containsName(String nm) {
            for (FieldDescriptor fd : paramForVar.values()) {
                if (nm.equals(fd.fieldName)) {
                    return true;
                }
            }
            return false;
        }

        String uniquify(String s) {
            String test = s;
            while (containsName(test)) {
                test = "_" + test;
            }
            return test;
        }

        String packageName() {
            String result = utils.packageName(origin);
            int ix = result.indexOf('(');
            if (ix > 0) {
                result = result.substring(0, ix);
            }
            return result;
        }

        void decorateClassWithConstraints(ClassBuilder<String> cb) {
            paramForVar.values().forEach(fd -> {
                fd.constraints.forEach(cn -> cn.decorateClass(cb));
            });
        }

        void generateIsSetMethod(ClassBuilder<String> cb) {
            boolean needed = false;
            for (FieldDescriptor v : paramForVar.values()) {
                if (v.optional && !v.constraints.isEmpty()) {
                    needed = true;
                    break;
                }
            }
            needed = true;
            if (needed) {
                cb.method("_isSet").withModifier(Modifier.PRIVATE)
                        .returning("boolean")
                        .addArgument("String", "__fieldName")
                        .body(bb -> {
                            bb.switchingOn("__fieldName", sw -> {
                                for (FieldDescriptor v : paramForVar.values()) {
                                    v.generateIsSetTest(SET_PRIM_FIELDS, sw);
                                }
                                sw.inDefaultCase(dc -> {
                                    dc.andThrow(nb -> {
                                        nb.withStringConcatentationArgument("Unknown field '")
                                                .appendExpression("__fieldName").append('\'')
                                                .endConcatenation().ofType("AssertionError");
                                    });
                                });
                            });
                        });
            }
        }

        ClassBuilder<String> generate() throws IOException {
            if (styles.contains(BuilderStyles.FLAT)) {
                return generateFlat();
            } else {
                return new CartesianGenerator.BuilderContext(origin, this).build();
            }
        }

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
                    .autoToString()) //                    .generateDebugLogCode()
                    ;

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
                String probName = uniquify("problems");
                bb.declare(probName).initializedWithNew()
                        .withArgument(4)
                        .ofType(ArrayList.class.getName() + "<>").as(List.class.getName() + "<String>");

                ClassBuilder.IfBuilder<?> iff = bb.iff().booleanExpression("(this." + SET_PRIM_FIELDS + " & " + this.allPrimitivesMask + "L) != " + this.allPrimitivesMask + "L");
                for (FieldDescriptor fd : paramForVar.values()) {
                    fd.generateValidityCheck(iff, probName);
                }
                iff.endIf();

                ClassBuilder.IfBuilder<?> conif = bb.iff().invoke("isEmpty").on(probName).isTrue().endCondition();
                for (FieldDescriptor fd : paramForVar.values()) {
                    fd.generateConstraints(probName, conif);
                }
                conif.endIf();

                ClassBuilder.IfBuilder<?> haveProblsBlock = bb.iff().invoke("isEmpty").on(probName).isFalse().endCondition();

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
            }
        }

        class FieldDescriptor implements Comparable<FieldDescriptor> {

            final Set<ConstraintGenerator> constraints;
            final VariableElement var;
            final boolean optional;
            String fieldName;
            private int primitiveIndex = -1;

            public FieldDescriptor(VariableElement var, boolean optional, String fieldName, Set<ConstraintGenerator> constraints) {
                this.var = var;
                this.optional = optional;
                this.fieldName = fieldName;
                this.constraints = constraints;
            }

            private int primitiveIndex() {
                if (primitiveIndex == -1 && isPrimitive()) {
                    indexPrimitives();
                }
                return primitiveIndex;
            }

            <C> void generateIsSetTest(String setMaskName, SwitchBuilder<C> sw) {
                if (optional) {
                    sw.inStringLiteralCase(fieldName)
                            .returning(true).endBlock();
                } else if (isPrimitive()) {
                    sw.inStringLiteralCase(fieldName)
                            .returning("( " + setMaskName + " & 1L << " + primitiveIndex() + ") != 0L")
                            .endBlock();
                } else {
                    sw.inStringLiteralCase(fieldName)
                            .returning("this." + fieldName + " != null")
                            .endBlock();
                }
            }

            public FieldDescriptor withFieldName(String fieldName) {
                this.fieldName = fieldName;
                return this;
            }

            String typeName() {
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
                    bb.lineComment("Flag bit " + pi);
                    bb.iff().booleanExpression("(this." + SET_PRIM_FIELDS + " & "
                            + (1L << pi) + "L) == 0")
                            .invoke("add")
                            .withStringConcatentationArgument(fieldName)
                            .append(" was not set and is required").endConcatenation()
                            .on(problemCollectionName)
                            .endIf();
                } else if (!optional && pi == -1) {
                    bb.lineComment("NOt optional: " + fieldName);

                    System.out.println("NOT OPTIONAL: " + fieldName);
                    bb.ifNull("this." + fieldName).invoke("add")
                            .withStringConcatentationArgument(fieldName)
                            .append(" was not set and is required")
                            .endConcatenation().on(problemCollectionName)
                            .endIf();
                } else {
                    bb.lineComment(fieldName + " is optional, no null-check required");
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
                generate(cb, true, false);
            }

            <C> void generate(ClassBuilder<C> cb, boolean createField, boolean runConstraints) {
                String tn = typeName();
                if (createField) {
                    cb.field(fieldName).withModifier(Modifier.PRIVATE).ofType(tn);
                }
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

                                        }).ofType("IllegalArgumentException");
                                    });
                                });
                            }
                            if (runConstraints && !constraints.isEmpty()) {
                                cb.importing(Consumer.class);
                                bb.declare("__failer")
                                        .initializedFromLambda().withArgument("_msg")
                                        .body().andThrow()
                                        .withArgument("_msg")
                                        .ofType("IllegalArgumentException")
                                        .as("Consumer<String>");
                                for (ConstraintGenerator c : constraints) {
                                    c.generate(fieldName, "__failer", "accept", utils, bb);
                                }
                            }
                            if (prim) {
                                bb.statement("this." + SET_PRIM_FIELDS
                                        + " |= " + (1L << primitiveIndex()) + "L");
                            }
                            bb.assign("this." + fieldName).toExpression(fieldName)
                                    .returningThis();
                        });
            }

            <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generateConstraints(String problemsVar, B into) {
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
                        System.out.println(tp + " is assignable to " + type + " - PRIMITIVE ");
                        return true;
                    }
                }
                System.out.println("NOT PRIMITIVE: " + type);
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

            public String toBulletPoint() {
                return "<li><code><b>" + fieldName + "</b> &emdash; " + simpleName(typeName()) + "</code></li>\n";
            }

            @Override
            public String toString() {
                return simpleName(typeName()) + " " + fieldName;
            }
        }
    }
}
