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

import static com.mastfrog.annotation.AnnotationUtils.capitalize;
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor;
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor.FieldDescriptor;
import static com.mastfrog.builder.annotation.processors.Utils.combine;
import static com.mastfrog.builder.annotation.processors.Utils.including;
import static com.mastfrog.builder.annotation.processors.Utils.omitting;
import static com.mastfrog.builder.annotation.processors.Utils.sorted;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilder;
import com.mastfrog.java.vogon.ClassBuilder.InvocationBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.NewBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import javax.lang.model.type.TypeKind;

/**
 *
 * @author Tim Boudreau
 */
public class Gen2Cartesian {

    private final BuilderDescriptor desc;
    private Map<String, OneBuilderModel> models = new HashMap<>();

    Gen2Cartesian(BuilderDescriptor desc) {
        this.desc = desc;
    }

    ClassBuilder<String> generate() {
        ClassBuilder<String> cb = ClassBuilder.forPackage(desc.packageName())
                .named(desc.builderName)
                .withModifier(Modifier.PUBLIC, Modifier.FINAL);
        initTree();
        List<OneBuilderModel> models = new ArrayList<>(this.models.values());
        Collections.sort(models, (a, b) -> {
            boolean aSans = a.name.contains("Sans");
            boolean bSans = b.name.contains("Sans");
            int result = aSans == bSans ? 0
                    : aSans && !bSans ? 1 : -1;
            if (result == 0) {
                result = Integer.compare(a.name.length(), b.name.length());
                if (result == 0) {
                    result = a.name.compareTo(b.name);
                }
            }
            return 0;
        });
        System.out.println("WILL GENERATE " + models.size() + " builders");
        for (OneBuilderModel m : models) {
            System.out.println(m);
            m.generateSkeleton(cb);
        }
        return cb;
    }

    private OneBuilderModel initTree() {
        OneBuilderModel root = rootBuilder();
        initOne(root, new HashSet<>());
        return root;
    }

    private void initOne(OneBuilderModel mdl, Set<String> seen) {
        if (seen.contains(mdl.name)) {
            return;
        }
        seen.add(mdl.name);
        for (OneBuilderModel child : mdl.children()) {
            initOne(child, seen);
        }
    }

    private OneBuilderModel rootBuilder() {
        return forFields(desc.requiredFields(), Collections.emptySet());
    }

    private String oneBuilderName(Collection<? extends FieldDescriptor> used,
            Collection<? extends FieldDescriptor> unused) {
        if (used.isEmpty()) {
            return desc.builderName;
        }
        StringBuilder sb = new StringBuilder(desc.builderName).append("With");
        for (FieldDescriptor fd : sorted(used)) {
            sb.append(capitalize(fd.fieldName));
        }
        StringBuilder sb2 = new StringBuilder(desc.builderName).append("Sans");
        for (FieldDescriptor fd : sorted(unused)) {
            sb2.append(capitalize(fd.fieldName));
        }
        if (sb2.length() < sb.length()) {
            return sb2.toString();
        }
        return sb.toString();
    }

    private OneBuilderModel forFields(Set<FieldDescriptor> unused, Set<FieldDescriptor> used) {
        String name = oneBuilderName(used, unused);
        OneBuilderModel m = models.computeIfAbsent(name, nm -> {

            return new OneBuilderModel(unused, used, desc.optionalFields());
        });
        return m;
    }

    private class OneBuilderModel implements Comparable<OneBuilderModel> {

        private final Set<FieldDescriptor> unusedFields;
        private final Set<FieldDescriptor> usedFields;
        private final Set<FieldDescriptor> optionalFields;
        private final List<String> explicitGenerics;
        private final List<String> implicitGenerics;
        private List<OneBuilderModel> children;
        private final String name;

        public OneBuilderModel(Set<FieldDescriptor> unusedFields, Set<FieldDescriptor> usedFields, Set<FieldDescriptor> optionalFields) {
            this.unusedFields = unusedFields;
            this.usedFields = usedFields;
            this.optionalFields = optionalFields;
            if (unusedFields.isEmpty()) {
                name = desc.builderName;
            } else {
                name = oneBuilderName(usedFields, unusedFields);
            }
            explicitGenerics = desc.genericSignatureForBuilderWith(combine(optionalFields, usedFields), GenericSignatureKind.EXPLICIT_BOUNDS);
            implicitGenerics = desc.genericSignatureForBuilderWith(combine(optionalFields, usedFields), GenericSignatureKind.IMPLICIT_BOUNDS);
        }

        <C> ClassBuilder<?> generateSkeleton(ClassBuilder<C> cb) {
            ClassBuilder<?> result = isRoot() ? cb : cb.innerClass(name)
                    .withModifier(Modifier.PUBLIC, Modifier.FINAL);
            if (result.topLevel() != result) {
                try {
                    result.withModifier(Modifier.STATIC);
                } catch (IllegalStateException ex) {
                    ex.printStackTrace(System.err);
                }
            }
            return _generateSkeleton(result);
        }

        public List<String> addedGenerics(OneBuilderModel creator) {
            List<String> newGenerics = new ArrayList<>(creator.explicitGenerics);
            newGenerics.removeAll(explicitGenerics);
            return newGenerics;
        }

        private <C> ClassBuilder<?> _generateSkeleton(ClassBuilder<C> result) {
            result.withTypeParameters(explicitGenerics);
            LocalFieldFactory<C> lff = LocalFieldFactory.create(desc, result);
            for (FieldDescriptor fd : usedFields) {
                lff.generatorFor(fd).generate(true);
            }
            for (FieldDescriptor fd : optionalFields) {
                lff.generatorFor(fd).generate(false);
            }
            result.constructor(con -> {
                if (isRoot()) {
                    con.setModifier(Modifier.PUBLIC);
                }
                for (FieldDescriptor fd : usedFields) {
                    con.addArgument(fd.typeName(), fd.fieldName);
                }
                if (!isRoot()) {
                    for (FieldDescriptor fd : optionalFields) {
                        con.addArgument(fd.typeName(), fd.fieldName);
                    }
                }
                con.body(bb -> {
                    for (FieldDescriptor fd : usedFields) {
                        bb.statement("this." + lff.generatorFor(fd).localFieldName()
                                + " = " + fd.fieldName);
                    }
                    if (!isRoot()) {
                        for (FieldDescriptor fd : optionalFields) {
                            bb.statement("this." + lff.generatorFor(fd).localFieldName()
                                    + " = " + fd.fieldName);
                        }
                    }
                });
            });
            UnsetCheckerFactory<C> ucf = UnsetCheckerFactory.create(result, desc, lff);
            SetterMethodFactory<C> smf = SetterMethodFactory.create(result, desc, lff, ucf);
            for (FieldDescriptor fd : optionalFields) {
                smf.generatorFor(fd).generate();
            }

            if (unusedFields.size() > 1) {
                for (FieldDescriptor fd : unusedFields) {
                    result.method("with" + capitalize(fd.fieldName), mb -> {
                        if (fd.canBeVarargs()) {
                            mb.withModifier(Modifier.FINAL)
                                    .annotatedWith("SafeVarargs").closeAnnotation();
                        }
                        mb.addArgument(fd.parameterTypeName(), fd.fieldName);
                        OneBuilderModel next = forFields(omitting(fd, unusedFields), including(fd, usedFields));
                        mb.returning(next.nameWithImplicitGenerics());
                        mb.withModifier(Modifier.PUBLIC);
                        mb.docComment(fd.setterJavadoc());
                        mb.withTypeParams(addedGenerics(next));

                        if (fd.canBeVarargs()) {
                            mb.withModifier(Modifier.FINAL);
                        }
                        mb.body(bb -> {
//                            generateNullCheck(fd, bb);
//                            generateConstraintsTest(result, bb, fd);
                            bb.returningNew(nb -> {
                                next.applyArgs(result, nb, lff, fd);
                                nb.ofType(next.nameWithImplicitGenerics());
                            });
                        });
                    });
                }
            } else if (unusedFields.size() == 1) {
                FieldDescriptor last = unusedFields.iterator().next();
                List<String> methodGens = desc.genericsRequiredFor(Collections.singleton(last));
                result.method("buildWith" + capitalize(last.fieldName), mb -> {
                    mb.addArgument(last.parameterTypeName(), last.fieldName);
                    mb.withModifier(Modifier.PUBLIC);
                    mb.docComment(last.setterJavadoc());
                    mb.returning(desc.targetTypeName);
                    if (last.canBeVarargs()) {
                        mb.withModifier(Modifier.FINAL)
                                .annotatedWith("SafeVarargs").closeAnnotation();
                    }
                    for (String mg : methodGens) {
                        if (!this.implicitGenerics.contains(mg)) {
                            mb.withTypeParam(desc.generics.nameWithBound(mg));
                        }
                    }
                    desc.thrownTypes().forEach(thrown -> {
                        if (thrown.getKind() != TypeKind.DECLARED) {
                            desc.utils().fail("Cannot handle generified thrown types", desc.origin);
                        }
                        mb.throwing(thrown.toString());
                    });
                    mb.body(bb -> {
                        generateNullCheck(last, bb);
                        bb.returningNew(nb -> {
                            for (FieldDescriptor fd : desc.fields()) {
                                if (fd != last) {
                                    nb.withArgument(lff.generatorFor(fd).localFieldName());
                                } else {
                                    generateInlineConstraintsTest(result, nb, fd);
                                }
                            }
                            boolean hasGenerics = !desc.genericsRequiredFor(desc.fields()).isEmpty();
                            nb.ofType(desc.targetTypeName + (hasGenerics ? "<>" : ""));
                        });
                    });

                });
            }
            result.build();
            return result;
        }

        public void generateNullCheck(FieldDescriptor fd, ClassBuilder.BlockBuilder<?> bb) {
            if (fd.isPrimitive()) {
                bb.ifNull(fd.fieldName)
                        .andThrow(nb -> {
                            nb.withStringConcatentationArgument(fd.fieldName)
                                    .append(" may not be null")
                                    .endConcatenation()
                                    .ofType("IllegalArgumentException");
                        });
            }
        }

        private <C> String generateConstraintsStaticMethod(ClassBuilder<C> result,
                FieldDescriptor fd) {
            ClassBuilder<?> top = result.topLevel();
            String failMethod = "fail__";
            if (!top.containsMethodNamed(failMethod)) {
                top.method(failMethod, mb -> {
                    mb.withTypeParam("X")
                            .returning("X")
                            .addArgument("String", "msg")
                            .withModifier(STATIC, PRIVATE)
                            .body()
                            .andThrow().withArgument("msg")
                            .ofType("IllegalArgumentException");
                });
            }
            String validateMethod = "validate" + capitalize(fd.fieldName) + "__";
            if (!top.containsMethodNamed(validateMethod)) {
                top.method(validateMethod, mb -> {
                    mb.withTypeParams(desc.genericsRequiredFor(Collections.singleton(fd)));
                    mb.addArgument(fd.typeName(), fd.fieldName)
                            .withModifier(PRIVATE, STATIC)
                            .returning(fd.typeName())
                            .body(bb -> {
                                if (!fd.isPrimitive()) {
                                    bb.ifNull(fd.fieldName)
                                            .returningInvocationOf(failMethod)
                                            .withStringConcatentationArgument(
                                                    fd.typeName()).append(" parameter ")
                                            .append(fd.fieldName)
                                            .append(" may not be null.")
                                            .endConcatenation().inScope();
                                }

                                List<ConstraintGenerator> cgs = fd.constraintsSorted();
                                for (ConstraintGenerator cg : cgs) {
                                    cg.decorateClass(result.topLevel());
                                    bb.lineComment("Weight " + cg.weight() + " " + cg.getClass().getSimpleName());
                                    cg.generate(fd.fieldName, result.topLevel().className(), failMethod, desc.utils(), bb, fd.fieldName);
                                }

                                bb.returning(fd.fieldName);
                            });
                });
            }
            return validateMethod;
        }

        public <C> void generateConstraintsTest(ClassBuilder<C> bldr,
                ClassBuilder.BlockBuilder<?> bb, FieldDescriptor fd) {

            bb.invoke(generateConstraintsStaticMethod(bldr, fd))
                    .withArgument(fd.fieldName).inScope();
        }

        public <C> void generateInlineConstraintsTest(ClassBuilder<C> bldr,
                InvocationBuilderBase<?, ?> ib, FieldDescriptor fd) {

            ib.withArgumentFromInvoking(generateConstraintsStaticMethod(bldr, fd))
                    .withArgument(fd.fieldName).inScope();
        }

        private void applyArgs(ClassBuilder<?> bldr, NewBuilder<?> nb, LocalFieldFactory<?> lff,
                FieldDescriptor adding) {
            for (FieldDescriptor fd : usedFields) {
                if (adding == fd) {
                    generateInlineConstraintsTest(bldr, nb, fd);
//                    nb.withArgument(fd.fieldName);
                } else {
                    nb.withArgument(lff.generatorFor(fd).localFieldName());
                }
            }
            for (FieldDescriptor fd : optionalFields) {
                if (adding == fd) {
                    generateInlineConstraintsTest(bldr, nb, fd);
//                    nb.withArgument(fd.fieldName);
                } else {
                    nb.withArgument(lff.generatorFor(fd).localFieldName());
                }
            }
        }

        private String nameWithImplicitGenerics() {
            StringBuilder sb = new StringBuilder(name);
            if (hasGenerics()) {
                sb.append('<');
                for (int i = 0; i < implicitGenerics.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(implicitGenerics.get(i));
                }
                sb.append('>');
            }
            return sb.toString();
        }

        List<OneBuilderModel> children() {
            if (children != null) {
                return children;
            }
            List<OneBuilderModel> result = new ArrayList<>();
            for (FieldDescriptor desc : unusedFields) {
                Set<FieldDescriptor> newUnused = Utils.omitting(desc, unusedFields);
                if (newUnused.isEmpty()) {
                    break;
                }
                Set<FieldDescriptor> newUsed = Utils.including(desc, usedFields);
                result.add(forFields(newUnused, newUsed));
            }
            return children = result;
        }

        boolean hasGenerics() {
            return !explicitGenerics.isEmpty();
        }

        boolean isRoot() {
            return usedFields.isEmpty();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(name);
            if (hasGenerics()) {
                sb.append('<');
                for (int i = 0; i < explicitGenerics.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(explicitGenerics.get(i));
                }
                sb.append('>');
            }
            return sb.toString();
        }

        @Override
        public int compareTo(OneBuilderModel o) {
            return name.compareToIgnoreCase(o.name);
        }
    }
}
