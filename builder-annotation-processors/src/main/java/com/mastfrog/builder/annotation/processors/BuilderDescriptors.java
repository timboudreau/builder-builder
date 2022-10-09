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
import com.mastfrog.builder.annotation.processors.spi.IsSetTestGenerator;
import com.mastfrog.java.vogon.ArgumentConsumer;
import com.mastfrog.java.vogon.ClassBuilder;

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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import static com.mastfrog.builder.annotation.processors.BuilderAnnotationProcessor.OPTIONALLY;
import java.util.HashSet;
import javax.lang.model.element.ElementKind;

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

    public static <T> ClassBuilder<T> initDebug(ClassBuilder<T> c) {
        return c.generateDebugLogCode();
    }

    public void add(Element e, Set<BuilderStyles> styles, String builderNameFromAnnotation,
            int codeGenerationVersion,
            Consumer<BuilderDescriptor> c) {
        c.accept(descs.computeIfAbsent(e, e1 -> new BuilderDescriptor(e, styles,
                builderNameFromAnnotation, codeGenerationVersion)));
    }

    public boolean isEmpty() {
        return descs.isEmpty();
    }

    public Optional<BuilderDescriptor> find(TypeElement desc) {
        if (desc == null) {
            new Exception("Descriptor is null").printStackTrace();
            return Optional.empty();
        }
        Optional<BuilderDescriptor> result = Optional.ofNullable(descs.get(desc));
        if (!result.isPresent()) {
            for (Map.Entry<Element, BuilderDescriptor> e : descs.entrySet()) {
                if (e.getKey().getKind() == ElementKind.CONSTRUCTOR) {
                    if (desc.equals(e.getKey().getEnclosingElement())) {
                        return Optional.of(e.getValue());
                    }
                }
            }
        }
        return result;
    }

    public void clear() {
        descs.clear();
    }

    public void generate() throws IOException {
        String oldName = Thread.currentThread().getName();
        Set<Element> toRemove = new HashSet<>();
        try {
            Filer filer = utils.processingEnv().getFiler();
            for (Map.Entry<Element, BuilderDescriptor> e : descs.entrySet()) {
                Thread.currentThread().setName("Generate " + e.getValue().builderName);
                ClassBuilder<String> cb = e.getValue().generate();
//            ClassBuilder<String> cb = new Gen2(e.getValue(), e.getValue().styles).generate();
                try {
                    JavaFileObject src = filer.createSourceFile(cb.fqn(), e.getValue().elements());
                    try ( OutputStream out = src.openOutputStream()) {
                        out.write(cb.toString().getBytes(UTF_8));
                    }
                    toRemove.add(e.getKey());
                } catch (FilerException ex) {
                    ex.printStackTrace(System.err);
                }
            }
        } finally {
            toRemove.forEach(descs::remove);
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

    static <B extends ClassBuilder<C>, C> B addGeneratedAnnotation(B cb) {
        cb.importing(Generated.class);
        cb.annotatedWith(Generated.class.getSimpleName(), ab -> ab.addArgument("value", BuilderAnnotationProcessor.class.getName()));
        return cb;
    }

    public class BuilderDescriptor {

        final Element origin;
        TypeElement instanceType;
        final Map<VariableElement, FieldDescriptor> paramForVar = new LinkedHashMap<>();
        final String builderName;
        final String targetTypeName;
        final Set<BuilderStyles> styles;
        final int codeGenerationVersion;
        final GenericsAnalyzer generics;

        BuilderDescriptor(Element e,
                Set<BuilderStyles> styles, String builderNameFromAnnotation,
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

        public TypeElement targetTypeElement() {
            TypeElement instanceType;
            switch (origin.getKind()) {
                case CONSTRUCTOR:
                    instanceType = (TypeElement) origin.getEnclosingElement();
                    break;
                case CLASS:
                case INTERFACE:
                    instanceType = (TypeElement) origin;
                    break;
                case METHOD:
                    instanceType = utils.processingEnv().getElementUtils()
                            .getTypeElement(((ExecutableElement) origin).getReturnType().toString());
                    break;
                default:
                    instanceType = utils.processingEnv().getElementUtils()
                            .getTypeElement(origin.asType().toString());
            }
            return instanceType;
        }

        public BuilderDescriptors owner() {
            return BuilderDescriptors.this;
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
            paramForVar.forEach((ve, d) -> all.add(ve));
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
                return addGeneratedAnnotation(new Gen2(this, styles).generate()).sortMembers();
            } else {
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

        class FieldDescriptor implements Comparable<FieldDescriptor>, Supplier<String> {

            final Set<ConstraintGenerator> constraints;
            final VariableElement var;
            final boolean optional;
            String fieldName;
            private int primitiveIndex = -1;
            final boolean nullValuesPermitted;

            final Optional<Defaulter> defaulter;

            public FieldDescriptor(VariableElement var, boolean optional,
                    String fieldName, Set<ConstraintGenerator> constraints) {
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

            public TypeMirror targetType() {
                return var.asType();
            }

            public TypeElement targetTypeElement() {
                TypeMirror mir = utils.erasureOf(targetType());
                TypeElement el = utils.processingEnv().getElementUtils()
                        .getTypeElement(mir.toString());
                return el;
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

            public String get() {
                return fieldName;
            }

            public boolean isReallyPrimitive() {
                return var.asType().getClass().getName().startsWith("Primitive");
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
                            if (tp.startsWith("java.lang.")) {
                                tp = tp.substring("java.lang.".length());
                            }
                            return capitalize(tp);
                    }
                }
                return tp;
            }

            String setterJavadoc() {
                StringBuilder sb = new StringBuilder("Sets the ")
                        .append(optional ? "<i>optional</i> " : "<b>required</b> ")
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
                sb.append("@param ").append(fieldName)
                        .append(" a ").append(this.var.asType())
                        .append("\n@return a builder");
                return sb.toString();
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

            @Override
            public int compareTo(FieldDescriptor o) {
                return fieldName.compareTo(o.fieldName);
            }

            @Override
            public String toString() {
                return simpleName(typeName()) + " " + fieldName;
            }
        }
    }
}
