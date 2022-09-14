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
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor;
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor.FieldDescriptor;
import static com.mastfrog.builder.annotation.processors.BuilderDescriptors.addGeneratedAnnotation;
import static com.mastfrog.builder.annotation.processors.BuilderDescriptors.initDebug;
import static com.mastfrog.builder.annotation.processors.Utils.combine;
import static com.mastfrog.builder.annotation.processors.Utils.including;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
public class CartesianGenerator {

    private final BuilderDescriptors descs;

    public CartesianGenerator(BuilderDescriptors descs) {
        this.descs = descs;
    }

    ClassBuilder<String> generateOne(Element el, BuilderDescriptors.BuilderDescriptor desc) {
        return new BuilderContext(el, desc).build();
    }

    private static String oneBuilderName(String rootBuilderName,
            Collection<? extends FieldDescriptor> used,
            Collection<? extends FieldDescriptor> unused) {
        if (used.isEmpty()) {
            return rootBuilderName;
        }
        StringBuilder sb = new StringBuilder(rootBuilderName).append("With");
        for (FieldDescriptor fd : Utils.sorted(used)) {
            sb.append(capitalize(fd.fieldName));
        }
        StringBuilder sb2 = new StringBuilder(rootBuilderName).append("Sans");
        for (FieldDescriptor fd : Utils.sorted(unused)) {
            sb2.append(capitalize(fd.fieldName));
        }
        if (sb2.length() < sb.length()) {
            return sb2.toString();
        }
        return sb.toString();
    }

    static class BuilderContext {

        private final Element el;
        private final BuilderDescriptors.BuilderDescriptor desc;
        private final Map<String, OneProductBuilder> cartBuilders = new HashMap<>();
        private final Map<String, OneProductBuilder> cartesians = new HashMap<>();

        public BuilderContext(Element el, BuilderDescriptors.BuilderDescriptor desc) {
            this.el = el;
            this.desc = desc;
        }

        ClassBuilder<String> build() {
            Set<FieldDescriptor> requiredFields = desc.requiredFields();
            Set<FieldDescriptor> optionalFields = desc.optionalFields();
            Set<FieldDescriptor> used = Collections.emptySet();
            OneProductBuilder opb = new OneProductBuilder(null, used,
                    requiredFields, optionalFields);
            cartesians.put(desc.builderName, opb);
            ClassBuilder<String> result = opb.build().parent;

            return result;
        }

        private String oneBuilderName(Collection<? extends FieldDescriptor> used,
                Collection<? extends FieldDescriptor> unused) {
            if (used.isEmpty()) {
                return desc.builderName;
            }
            StringBuilder sb = new StringBuilder(desc.builderName).append("With");
            for (FieldDescriptor fd : Utils.sorted(used)) {
                sb.append(capitalize(fd.fieldName));
            }
            StringBuilder sb2 = new StringBuilder(desc.builderName).append("Sans");
            for (FieldDescriptor fd : Utils.sorted(unused)) {
                sb2.append(capitalize(fd.fieldName));
            }
            if (sb2.length() < sb.length()) {
                return sb2.toString();
            }
            return sb.toString();
        }

        private OneProductBuilder subBuilder(FieldDescriptor applying,
                ClassBuilder<String> parent,
                Set<FieldDescriptor> used,
                Set<FieldDescriptor> required, Set<FieldDescriptor> optional) {

            String nm = oneBuilderName(Utils.including(applying, used), Utils.omitting(applying, required));
            OneProductBuilder opb = cartesians.get(nm);
            if (opb == null) {
                opb = new OneProductBuilder(parent, Utils.including(applying, used),
                        Utils.omitting(applying, required), optional);
                cartesians.put(nm, opb);
                return opb;
            }
            return null;
        }

        private ClassBuilder<String> parentBuilder(ClassBuilder<String> parent) {
            if (parent == null) {
                List<String> genericsForOptionals = desc.initialGenerics(GenericSignatureKind.EXPLICIT_BOUNDS);

                parent = initDebug(addGeneratedAnnotation(ClassBuilder.forPackage(desc.packageName())
                        .named(desc.builderName))
                        .withModifier(Modifier.FINAL)
                        .withTypeParameters(genericsForOptionals)
                        //                        .conditionally(!genericsForOptionals.isEmpty(), cb -> {
                        //                            cb.withTypeParameters(genericsForOptionals);
                        //                        })
                        .conditionally(!desc.styles.contains(BuilderStyles.PACKAGE_PRIVATE), cb -> {
                            cb.withModifier(Modifier.PUBLIC);
                        }).autoToString()
                        .conditionally(desc.instanceType == null, clb -> {
                            clb.constructor(con -> {
                                con.setModifier(Modifier.PUBLIC)
                                        .body().endBlock();
                            });
                        }));
//                parent.generateDebugLogCode();
                desc.decorateClassWithConstraints(parent);
                if (desc.instanceType != null) {
                    String instField = desc.uniquify("instance");
                    parent.field(instField).withModifier(Modifier.FINAL, Modifier.PRIVATE)
                            .ofType(desc.instanceType.asType().toString());
                    parent.constructor(cb -> {
                        cb.setModifier(Modifier.PUBLIC)
                                .addArgument(desc.instanceType.asType().toString(), "instance")
                                .body(bb -> {
                                    bb.ifNull(instField).andThrow(nb -> {
                                        nb.withStringConcatentationArgument("An instance of")
                                                .append(simpleName(desc.instanceType.asType()))
                                                .append(" is required to create the builder")
                                                .append(" and may not be null")
                                                .endConcatenation()
                                                .ofType("IllegalArgumentException");
                                    }).endIf();
                                    bb.assign("this." + instField).toExpression(instField);
                                });
                    });
                }
            }
            return parent;
        }

        private static <T> boolean noOverlap(Set<T> a, Set<T> b) {
            Set<T> x = new HashSet<>(a);
            x.retainAll(b);
            return x.isEmpty();
        }

        private Set<String> appliersGenerated = new HashSet<>();

        class OneProductBuilder {

            private final ClassBuilder<String> parent;
            private final ClassBuilder<?> target;
            private final Set<FieldDescriptor> used;
            private final Set<FieldDescriptor> unused;
            private final Set<FieldDescriptor> optional;
            private final List<String> generics;
            private String nm;

            OneProductBuilder(ClassBuilder<String> parent, Set<FieldDescriptor> used,
                    Set<FieldDescriptor> required, Set<FieldDescriptor> optional) {
                if (!noOverlap(required, used)) {
                    throw new AssertionError("Overlap " + required + " and " + used);
                }
                this.parent = parentBuilder(parent);
                this.used = used;
                this.unused = required;
                this.optional = optional;
                generics = desc.genericSignatureForBuilderWith(used, GenericSignatureKind.IMPLICIT_BOUNDS);
                List<String> fullGenerics
                        = desc.genericSignatureForBuilderWith(used, GenericSignatureKind.EXPLICIT_BOUNDS);
//                generics = desc.genericsRequiredFor(combine(used, optional));
                nm = oneBuilderName(used, required);
                if (parent == null) {
                    target = this.parent;
                } else {
                    target = addGeneratedAnnotation(
                            parent
                                    .innerClass(nm)
                                    .withModifier(Modifier.STATIC, Modifier.FINAL)
                                    .conditionally(!desc.styles.contains(BuilderStyles.PACKAGE_PRIVATE), cb -> {
                                        cb.withModifier(Modifier.PUBLIC);
                                    })
                                    .withTypeParameters(fullGenerics)
                                    //                                    .conditionally(!generics.isEmpty(), cb -> {
                                    //                                        List<String> genericsWithBounds = new ArrayList<>();
                                    //                                        for (String g : generics) {
                                    //                                            g = desc.withGenericBound(g);
                                    //                                            genericsWithBounds.add(g);
                                    //                                        }
                                    //                                        cb.withTypeParameters(genericsWithBounds);
                                    //                                    })
                                    .autoToString());
                    applyDocComments();

                    if (desc.instanceType != null) {
                        String instField = desc.uniquify("instance");
                        target.field(instField).withModifier(Modifier.FINAL, Modifier.PRIVATE)
                                .ofType(desc.instanceType.asType().toString());
                    }
                }
            }

            private void applyDocComments() {
                List<String> stuff = new ArrayList<>();
                stuff.add("Intermediate builder of a " + AnnotationUtils.simpleName(desc.targetTypeName) + ".\n");
                if (!used.isEmpty()) {
                    stuff.add("<h3>Already Set Fields</h3>\n");
                    stuff.add("<ul>\n");
                    for (FieldDescriptor fd : used) {
                        stuff.add(fd.toBulletPoint());
                    }
                    stuff.add("</ul>");
                }
                if (!unused.isEmpty()) {
                    stuff.add("\n<h3>Remaining Fields</h3>\n");
                    stuff.add("<ul>");
                    for (FieldDescriptor fd : unused) {
                        stuff.add(fd.toBulletPoint());
                    }
                    stuff.add("</ul>");
                }
                if (!optional.isEmpty()) {
                    stuff.add("<h3>Optional Fields</h3>\n");
                    stuff.add("<ul>");
                    for (FieldDescriptor fd : optional) {
                        stuff.add(fd.toBulletPoint());
                    }
                    stuff.add("</ul>");
                }
                target.docComment((Object[]) stuff.toArray(new String[stuff.size()]));
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
                if (target == parent) {
                    return;
                }
                target.constructor(cb -> {
                    List<FieldDescriptor> existing = assignedFields();
                    for (FieldDescriptor fd : existing) {
                        cb.addArgument(fd.typeName(), fd.fieldName);
                    }
                    String instField = desc.uniquify("instance");
                    if (desc.instanceType != null) {
                        cb.addArgument(desc.instanceType.asType().toString(), instField);
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
                        if (desc.instanceType != null) {
                            bb.assign("this." + instField).toExpression(instField);
                        }
                    });
                });
            }

            private void generateBuildMethod() {
                if (unused.size() == 1) {
                    FieldDescriptor last = unused.iterator().next();
                    List<String> methodGenerics = desc.genericsRequiredFor(Collections.singleton(last));
                    methodGenerics.removeAll(generics);
                    String typeToBuild = desc.targetTypeName;
                    String retGenerics = desc.fullTargetGenerics();
                    target.method("buildWith" + capitalize(last.fieldName))
                            .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                            .addArgument(last.parameterTypeName(), last.fieldName)
                            .conditionally(last.canBeVarargs(), mb -> {
                                mb.annotatedWith(SafeVarargs.class.getName()).closeAnnotation();
                            })
                            .conditionally(!methodGenerics.isEmpty(), mg -> {
                                for (String g : methodGenerics) {
                                    mg.withTypeParam(desc.withGenericBound(g));
                                }
                            })
                            .returning(typeToBuild + retGenerics)
                            .conditionally(!desc.thrownTypes().isEmpty(), mb -> {
                                for (TypeMirror tm : desc.thrownTypes()) {
                                    mb.throwing(tm.toString());
                                }
                            })
                            .body(bb -> {
                                bb.lineComment("b. TargetType " + desc.targetTypeName);
                                bb.lineComment("b. TargetFqn " + desc.targetFqn());
                                bb.lineComment("b. fullTargetGenerics " + desc.fullTargetGenerics());
                                bb.lineComment("b. fullTargetGenericSignature " + desc.fullTargetGenericSignature());

                                if (!last.isPrimitive()) {
                                    bb.ifNull(last.fieldName)
                                            .andThrow(nb -> {
                                                nb.withStringConcatentationArgument(last.fieldName)
                                                        .append(" may not be null.")
                                                        .endConcatenation().ofType("IllegalArgumentException");
                                            });
                                }
                                if (!last.constraints.isEmpty()) {
                                    target.importing(Consumer.class);
                                    bb.declare("__failer")
                                            .initializedFromLambda().withArgument("_msg")
                                            .body().andThrow()
                                            .withArgument("_msg")
                                            .ofType("IllegalArgumentException")
                                            .as("Consumer<String>");

                                    for (ConstraintGenerator c : last.constraints) {
                                        c.generate(last.fieldName, "__failer", "accept", desc.utils(), bb,
                                                last.fieldName);
                                    }
                                }
                                if (desc.instanceType != null) {
                                    ExecutableElement ee = (ExecutableElement) desc.origin;
                                    String methodName = ee.getSimpleName().toString();
                                    String instField = desc.uniquify("instance");
                                    ClassBuilder.InvocationBuilder<?> inv = bb.returningInvocationOf(methodName);
                                    for (FieldDescriptor desc : desc.paramForVar.values()) {
                                        if (desc == last) {
                                            inv.withArgument(last.fieldName);
                                        } else {
                                            desc.generate(inv);
                                        }
                                    }
                                    inv.onField(instField).ofThis();
                                } else {
                                    bb.returningNew(nb -> {
                                        for (FieldDescriptor fd : desc.paramForVar.values()) {
                                            if (fd != last) {
                                                nb.withArgumentFromField(fd.fieldName).ofThis();
                                            } else {
                                                nb.withArgument(fd.fieldName);
                                            }
                                        }
                                        if (desc.instanceType != null) {
                                            String instField = desc.uniquify("instance");
                                            nb.withArgument("this." + instField);
                                        }
                                        nb.ofType(typeToBuild + (retGenerics.isEmpty() ? "" : "<>"));
                                    });
                                }
                            });
                }
            }

            private void generateOptionalSetters() {
                for (FieldDescriptor fd : Utils.sorted(optional)) {
                    fd.generate(target, false, true);
                }
            }

            private void generateRequiredSetters() {
                if (unused.size() == 1) {
                    return;
                }
                for (FieldDescriptor fd : Utils.sorted(unused)) {
                    List<FieldDescriptor> args = Utils.sorted(
                            combine(
                                    including(fd, used),
                                    optional)
                    );

                    String builderName = oneBuilderName(Utils.including(fd, used), Utils.omitting(fd, unused));
//
//                    List<String> methodGenerics = desc.genericSignatureForBuilderWith(combine(optional, including(fd, used)),
//                            GenericSignatureKind.EXPLICIT_BOUNDS);
//
////                    String retTypeGenerics = desc.genericSignatureForMethodAdding(fd, used);
//                    String retTypeGenerics = desc.builderGenericSignature(
//                            combine(Collections.singleton(fd), used), GenericSignatureKind.IMPLICIT_BOUNDS);
//
//                    String retType = builderName + retTypeGenerics;
//                    System.out.println("GEN SIG ADDINg " + fd + " " + builderName + " generics " + retTypeGenerics);

                    List<String> genericsForFd = desc.genericsRequiredFor(Collections.singleton(fd));
                    genericsForFd.removeAll(generics);

                    List<String> genericsSig = desc.genericsRequiredFor(Utils.combine(combine(used, Collections.singleton(fd)), optional));
                    StringBuilder unqualified = new StringBuilder(builderName);
                    for (String gs : genericsSig) {
                        String orig = gs;
                        gs = desc.withGenericBound(gs);
                        if (unqualified.length() == builderName.length()) {
                            unqualified.append('<');
                        } else {
                            unqualified.append(", ");
                        }
                        unqualified.append(orig);
                    }
                    boolean hasGenerics = unqualified.length() != builderName.length();
                    if (hasGenerics) {
                        unqualified.append('>');
                    }
                    BuilderDescriptor sib = fd.siblingBuilder();
                    if (sib != null) {
                        String name;
                        if (!sib.isSamePackage(desc)) {
                            name = sib.packageName() + "." + sib.builderName;
                        } else {
                            name = sib.builderName;
                        }
                        String ifaceName;
                        if (!sib.thrownTypes().isEmpty()) {
                            ifaceName = sib.builderName + "Applier";
                            if (appliersGenerated.add(ifaceName)) {
                                parent.innerClass(sib.builderName + "Applier")
                                        .toInterface()
                                        .docComment("Function-like interface for methods that instantiate a builder ",
                                                "which captures the exception signature of ", sib.targetTypeName + "'s constructor.")
                                        .annotatedWith(FunctionalInterface.class.getName()).closeAnnotation()
                                        .withModifier(Modifier.PUBLIC)
                                        .method("apply", ifb -> {
                                            for (TypeMirror th : sib.thrownTypes()) {
                                                ifb.throwing(th.toString());
                                            }
                                            ifb.addArgument(name, "bldr")
                                                    .returning(fd.typeName());
//                                            ifb.closeMethod();
                                        })
                                        .build();
                            }
                        } else {
                            ifaceName = "Function<? super " + name + ", ? extends "
                                    + fd.typeName() + ">";
                        }

                        target.importing(Function.class);
                        target.method("with" + capitalize(fd.fieldName))
                                .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                                .addArgument(ifaceName, fd.fieldName + "BuilderApplier")
                                .conditionally(fd.canBeVarargs(), mb -> {
                                    mb.annotatedWith(SafeVarargs.class.getName()).closeAnnotation();
                                })
                                //                                .addArgument(fd.typeName(), fd.fieldName)
                                .docComment(fd.setterJavadoc())
                                .conditionally(!genericsForFd.isEmpty(), mb -> {
                                    for (String g : genericsForFd) {
                                        mb.withTypeParam(desc.withGenericBound(g));
                                    }
                                })
                                .conditionally(!sib.thrownTypes().isEmpty(), mb -> {
                                    for (TypeMirror th : sib.thrownTypes()) {
                                        mb.throwing(th.toString());
                                    }
                                })
                                .returning(unqualified.toString())
                                .body(bb -> {
                                    String b = desc.uniquify("builder");
                                    bb.declare(b).initializedWithNew().ofType(name).as(name);
                                    bb.returningInvocationOf("with" + capitalize(fd.fieldName))
                                            .withArgumentFromInvoking("apply")
                                            .withArgument(b)
                                            .on(fd.fieldName + "BuilderApplier")
                                            .onThis();
                                });
                    }

                    target.method("with" + capitalize(fd.fieldName))
                            .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                            .addArgument(fd.parameterTypeName(), fd.fieldName)
                            .docComment(fd.setterJavadoc())
                            .conditionally(!genericsForFd.isEmpty(), mb -> {
                                for (String g : genericsForFd) {
                                    mb.withTypeParam(desc.withGenericBound(g));
                                }
                            })
                            .returning(unqualified.toString())
                            .body(bb -> {
                                if (!fd.isPrimitive()) {
                                    bb.ifNull(fd.fieldName)
                                            .andThrow(nb -> {
                                                nb.withStringConcatentationArgument(fd.fieldName)
                                                        .append(" may not be null.")
                                                        .endConcatenation().ofType("IllegalArgumentException");
                                            }).endIf();
                                }
                                if (!fd.constraints.isEmpty()) {
                                    target.importing(Consumer.class);
                                    bb.declare("__failer")
                                            .initializedFromLambda().withArgument("_msg")
                                            .body().andThrow()
                                            .withArgument("_msg")
                                            .ofType("IllegalArgumentException")
                                            .as("Consumer<? super String>");

                                    for (ConstraintGenerator c : fd.constraints) {
                                        c.generate(fd.fieldName, "__failer", "accept", desc.utils(), bb, fd.fieldName);
                                    }
                                }
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
                                    if (desc.instanceType != null) {
                                        String instField = desc.uniquify("instance");
                                        nb.withArgument("this." + instField);
                                    }
                                    nb.ofType(builderName + (hasGenerics ? "<>" : ""));
                                });
                                if (opb != null) {
                                    opb.build();
                                }
                            });
                }
            }

            public OneProductBuilder build() {
                generateConstructor();
                generateFields();
                generateOptionalSetters();
                generateRequiredSetters();
                generateBuildMethod();
                target.build();
                return this;
            }
        }
    }
}
