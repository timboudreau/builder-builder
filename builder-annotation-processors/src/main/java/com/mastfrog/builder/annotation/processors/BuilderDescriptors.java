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
import static com.mastfrog.builder.annotation.processors.Utils.combine;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.builder.annotation.processors.spi.IsSetTestGenerator;
import com.mastfrog.java.vogon.ArgumentConsumer;
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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.Generated;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import static com.mastfrog.builder.annotation.processors.BuilderAnnotationProcessor.OPTIONALLY;

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

    public void add(Element e, Set<BuilderStyles> styles, String builderNameFromAnnotation,
            int codeGenerationVersion,
            Consumer<BuilderDescriptor> c) {
        c.accept(descs.computeIfAbsent(e, e1 -> new BuilderDescriptor(e, styles,
                builderNameFromAnnotation, codeGenerationVersion)));
    }

    public void generate() throws IOException {
        String oldName = Thread.currentThread().getName();
        try {
            Filer filer = utils.processingEnv().getFiler();
            for (Map.Entry<Element, BuilderDescriptor> e : descs.entrySet()) {
                Thread.currentThread().setName("Generate " + e.getValue().builderName);
                ClassBuilder cb = e.getValue().generate();
//            ClassBuilder<String> cb = new Gen2(e.getValue(), e.getValue().styles).generate();
                try {
                    JavaFileObject src = filer.createSourceFile(cb.fqn(), e.getValue().elements());
                    try ( OutputStream out = src.openOutputStream()) {
                        out.write(cb.toString().getBytes(UTF_8));
                    }
                } catch (FilerException ex) {
                    ex.printStackTrace(System.err);
                }
            }
        } finally {
            Thread.currentThread().setName(oldName);
        }
    }
    static final String[] PRIMITIVE_AND_BOXED_TYPES = {
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

    static final String[] PRIMITIVE_TYPES = {
        Byte.TYPE.getName(),
        Short.TYPE.getName(),
        Integer.TYPE.getName(),
        Long.TYPE.getName(),
        Character.TYPE.getName(),
        Float.TYPE.getName(),
        Double.TYPE.getName(),
        Boolean.TYPE.getName(),
        Void.TYPE.getName(),};

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

    static String composeGenericSig(Collection<? extends String> strings) {
        if (strings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String s : strings) {
            if (sb.length() != 0) {
                sb.append(", ");
            }
            sb.append(s);
        }
        String result = sb.insert(0, '<').append('>').toString();
        return result;
    }

    public class BuilderDescriptor {

        private static final String SET_PRIM_FIELDS = "__setPrimitiveFields";

        final Element origin;
        TypeElement instanceType;
        final Map<VariableElement, FieldDescriptor> paramForVar = new LinkedHashMap<>();
        String builderName;
        String targetTypeName;
        long allPrimitivesMask;
        final Set<BuilderStyles> styles;
        final int codeGenerationVersion;
        final GenericsAnalyzer generics;

        BuilderDescriptor(Element e, Set<BuilderStyles> styles, String builderNameFromAnnotation,
                int codeGenerationVersion) {
            this.origin = e;
            this.styles = styles;
            this.codeGenerationVersion = codeGenerationVersion;
            String nm, bn;
            switch (e.getKind()) {
                case METHOD:
                    ExecutableElement ex = (ExecutableElement) e;
                    nm = AnnotationUtils.simpleName(ex.getReturnType());
                    bn = nm + "Builder";
                    break;
                default:
                    nm = AnnotationUtils.enclosingType(e).getSimpleName().toString();
                    String[] parts = nm.split("[\\.\\$]");
                    if (parts.length > 0) {
                        bn = parts[parts.length - 1] + "Builder";
                    } else {
                        bn = nm + "Builder";
                    }
            }
            if (builderNameFromAnnotation != null) {
                bn = builderNameFromAnnotation;
            }
            this.builderName = bn;
            this.targetTypeName = nm;
            generics = new GenericsAnalyzer(utils, (ExecutableElement) e);
        }

        public List<FieldDescriptor> fields() {
            return new ArrayList<>(paramForVar.values());
        }

        public String targetFqn() {
            return packageName() + "." + targetTypeName;
        }

        public boolean isSamePackage(BuilderDescriptor other) {
            return other == this || utils.packageName(origin).equals(utils.packageName(other.origin));
        }

        public String withGenericBound(String generic) {
            return generics.nameWithBound(generic);
        }

        public List<? extends TypeMirror> thrownTypes() {
            return ((ExecutableElement) origin).getThrownTypes();
        }

        public List<String> genericsRequiredFor(Collection<FieldDescriptor> flds) {
            return generics.genericNamesRequiredFor(flds);
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

        public String initialGenericsSig(GenericSignatureKind kind) {
            if (genericsRequiredFor(this.optionalFields()).isEmpty()) {
                return "";
            }
            return composeGenericSig(initialGenerics(kind));
        }

        public List<String> initialGenerics(GenericSignatureKind kind) {
            List<String> result;
            switch (kind) {
                case EXPLICIT_BOUNDS:
                    return fullSignatures(genericsRequiredFor(this.optionalFields()));
                case IMPLICIT_BOUNDS:
                case INFERRED_BOUNDS:
                    return genericsRequiredFor(this.optionalFields());
                default:
                    throw new AssertionError(kind);
            }
        }

        private List<String> fullSignatures(Collection<? extends String> of) {
            List<String> result = new ArrayList<>(of.size());
            for (String o : of) {
                result.add(generics.nameWithBound(o));
            }
            return result;
        }

        public List<String> genericSignatureForBuilderWith(Collection<? extends FieldDescriptor> c, GenericSignatureKind kind) {
            List<String> result = generics.genericNamesRequiredFor(Utils.combine(c, optionalFields()));
            switch (kind) {
                case EXPLICIT_BOUNDS:
                    return fullSignatures(result);
                default:
                    return result;
            }
        }

        public String builderGenericSignature(Collection<? extends FieldDescriptor> c, GenericSignatureKind k) {
            List<String> result = generics.genericNamesRequiredFor(Utils.combine(c, optionalFields()));
            switch (k) {
                case EXPLICIT_BOUNDS:
                    return composeGenericSig(fullSignatures(result));
                case IMPLICIT_BOUNDS:
                    return composeGenericSig(result);
                case INFERRED_BOUNDS:
                    return result.isEmpty() ? "" : "<>";
                default:
                    throw new AssertionError(k);
            }
        }

        public String genericSignatureForMethodAdding(FieldDescriptor fd, Collection<? extends FieldDescriptor> alreadyPresent) {
            List<String> all = generics.genericNamesRequiredFor(Collections.singleton(fd));
            if (all.isEmpty()) {
                return "";
            }
            // These will already be present
            all.removeAll(generics.genericNamesRequiredFor(this.optionalFields()));
            all.removeAll(generics.genericNamesRequiredFor(alreadyPresent));
            if (all.isEmpty()) {
                return "";
            }
            return composeGenericSig(all);
        }

        public String fullTargetGenerics() {
            List<String> gens = genericsRequiredFor(this.paramForVar.values());
            if (gens.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String g : gens) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(g);
            }
            return sb.insert(0, '<').append('>').toString();
        }

        public String fullTargetGenericSignature() {
            List<String> gens = genericsRequiredFor(this.paramForVar.values());
            if (gens.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String g : gens) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(withGenericBound(g));
            }
            return sb.insert(0, '<').append('>').toString();
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
            boolean flat = styles.contains(BuilderStyles.FLAT);
            int reqCount = requiredFields().size();
            if (!flat && reqCount > 10) {
                utils().warn(builderName + " cannot use cartesian mode - it "
                        + "would require " + ((long) Math.pow(2, reqCount))
                        + " nested classes, which will likely run past javac's "
                        + "code size and line line limits", origin);
                flat = true;
            } else if (reqCount == 0) {
                flat = true;
            }
            if (flat) {
//                return generateFlat();
                return addGeneratedAnnotation(new Gen2(this, styles).generate()).sortMembers();
            } else {
//                return new CartesianGenerator.BuilderContext(origin, this).build();
                return addGeneratedAnnotation(new Gen2Cartesian(this).generate()).sortMembers();
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
                    .withTypeParameters(fullSignatures(generics.genericNamesRequiredFor(paramForVar.values())));

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
            List<String> gens = generics.genericNamesRequiredFor(paramForVar.values());
            ClassBuilder.MethodBuilder<ClassBuilder<String>> mb = cb.method("build")
                    .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                    .returning(targetTypeName + composeGenericSig(gens))
                    .conditionally(!thrownTypes().isEmpty(), mmb -> {
                        for (TypeMirror tm : thrownTypes()) {
                            mmb.throwing(tm.toString());
                        }
                    });
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
                        String withGenerics = this.initialGenericsSig(GenericSignatureKind.INFERRED_BOUNDS);
                        nb.ofType(targetTypeName + (gens.isEmpty() ? "" : "<>"));
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
                        .withArgument(paramForVar.size())
                        .ofType(ArrayList.class.getName() + "<>").as(List.class.getName() + "<String>");

                ClassBuilder.IfBuilder<?> iff = bb.iff().booleanExpression("(this." + SET_PRIM_FIELDS + " & " + this.allPrimitivesMask + "L) != " + this.allPrimitivesMask + "L");
                for (FieldDescriptor fd : paramForVar.values()) {
                    if (fd.isPrimitive()) {
                        fd.generateValidityCheck(iff, probName);
                    }
                }
                iff.endIf();
                for (FieldDescriptor fd : paramForVar.values()) {
                    if (!fd.isPrimitive()) {
                        fd.generateValidityCheck(bb, probName);
                    }
                }

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

        class FieldDescriptor implements Comparable<FieldDescriptor>, Supplier<String> {

            final Set<ConstraintGenerator> constraints;
            final VariableElement var;
            final boolean optional;
            String fieldName;
            private int primitiveIndex = -1;
            final boolean nullValuesPermitted;

            Optional<Defaulter> defaulter;

            public FieldDescriptor(VariableElement var, boolean optional, String fieldName, Set<ConstraintGenerator> constraints) {
                this.var = var;
                this.optional = optional;
                this.fieldName = fieldName;
                this.constraints = constraints;
                Defaulter def = null;
                if (optional) {
                    AnnotationMirror mir = utils.findAnnotationMirror(var, OPTIONALLY);
                    if (mir != null) {
                        def = Defaulter.forAnno(var, var.asType(), mir, utils);
                        nullValuesPermitted = utils.annotationValue(mir, "acceptNull", Boolean.class, Boolean.FALSE);
                    } else {
                        nullValuesPermitted = false;
                    }
                } else {
                    nullValuesPermitted = false;
                }
                defaulter = Optional.ofNullable(def);
            }

            public boolean isValidatable() {
                if (!isPrimitive() && optional) {
                    return !nullValuesPermitted;
                }
                if (isReallyPrimitive()) {
                    return !constraints.isEmpty();
                }
                if (isPrimitive() && !optional) {
                    return !constraints.isEmpty();
                }
                if (constraints.isEmpty()) {
                    return !nullValuesPermitted;
                }
                return false;
            }

            public List<ConstraintGenerator> constraintsSorted() {
                List<ConstraintGenerator> result = new ArrayList<>(constraints);
                Collections.sort(result);
                return result;
            }

            public int constraintWeightSum() {
                int result = 0;
                for (ConstraintGenerator cg : constraints) {
                    result += cg.weight();
                }
                return result;
            }

            <V> void applyParam(String localFieldName, IsSetTestGenerator test, ArgumentConsumer<V> ib, ClassBuilder<?> cb) {
                defaulter.ifPresentOrElse(def -> {
                    def.generate(localFieldName, test, ib.withArgument(), cb);
                },
                        () -> ib.withArgument(localFieldName));
            }

//            <V> void applyParam(String localFieldName, IsSetTestGenerator test, NewBuilder<V> ib, ClassBuilder<?> cb) {
//                defaulter.ifPresentOrElse(def -> {
//                    def.generate(localFieldName, test, ib.withArgument(),cb
//                    );
//                },
//                        () -> ib.withArgument(localFieldName));
//            }
            public BuilderDescriptor siblingBuilder() {
                for (Map.Entry<Element, BuilderDescriptor> e : BuilderDescriptors.this.descs.entrySet()) {
                    if (e.getValue() == BuilderDescriptor.this) {
                        continue;
                    }
                    // XXX this comparison may compare differently named generics and fail
                    if (e.getValue().targetFqn().equals(var.asType().toString())) {
                        return e.getValue();
                    }
                }
                return null;
            }

            public String get() {
                return fieldName;
            }

            public boolean isReallyPrimitive() {
                return var.asType().getClass().getName().startsWith("Primitive");
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
                            .returning("( " + setMaskName + " & " + (1L << primitiveIndex()) + ") != 0L")
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

            boolean canBeVarargs() {
                return this.var.asType().getKind() == TypeKind.ARRAY && this.var.asType() instanceof ArrayType;
            }

            String parameterTypeName() {
                if (canBeVarargs()) {
                    ArrayType at = (ArrayType) this.var.asType();
                    return at.getComponentType() + "...";
                }
                return typeName();
            }

            String typeName() {
                String tp = var.asType().toString();
                if (isPrimitive() && optional) {
                    switch (tp) {
                        case "char":
                            return "Character";
                        case "int":
                            return "Integer";
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
                        .append(optional ? "<i>optional</i>" : "<b>required</b> ")
                        .append("parameter <code>")
                        .append(fieldName).append("</code> of ")
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
                String gens = "";
                if (runConstraints) {
                    gens = BuilderDescriptor.this.builderGenericSignature(paramForVar.values(), GenericSignatureKind.IMPLICIT_BOUNDS);
                } else {
                    gens = BuilderDescriptor.this.builderGenericSignature(paramForVar.values(), GenericSignatureKind.IMPLICIT_BOUNDS);
                }
                cb.method("with" + capitalize(fieldName))
                        .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                        .addArgument(parameterTypeName(), fieldName)
                        .conditionally(canBeVarargs(), mb -> {
                            mb.annotatedWith(SafeVarargs.class.getName()).closeAnnotation();
                        })
                        .docComment(setterJavadoc())
                        .returning(cb.className() + gens)
                        .body(bb -> {
                            boolean prim = isPrimitive();
                            bb.lineComment("Primitive type checks needed? " + prim);
                            if (!prim && !optional) {
                                bb.ifNull(fieldName, ib -> {
                                    ib.andThrow(nb -> {
                                        nb.withArgument(sc -> {
                                            sc.stringConcatenation().expression(fieldName)
                                                    .append(" may not be null.")
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
                                    c.generate(fieldName, "__failer", "accept", utils, bb, fieldName);
                                }
                            }
                            if (prim && !optional && !runConstraints) {
                                bb.statement("this." + SET_PRIM_FIELDS
                                        + " |= " + (1L << primitiveIndex()) + "L");
                            }
                            bb.assign("this." + fieldName).toExpression(fieldName)
                                    .returningThis();
                        });
            }

            <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generateConstraints(String problemsVar, B into) {
                for (ConstraintGenerator gen : this.constraints) {
                    gen.generate(fieldName, problemsVar, "add", utils, into, fieldName);
                }
            }

            <C> void generate(InvocationBuilder<C> ib) {
                ib.withArgumentFromField(fieldName).of("this");
            }

            boolean isPrimitive() {
                TypeMirror type = var.asType();
                for (String tp : PRIMITIVE_AND_BOXED_TYPES) {
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
