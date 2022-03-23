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

import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor;
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor.FieldDescriptor;
import com.mastfrog.builder.annotation.processors.LocalFieldFactory.LocalFieldGenerator;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
public class BuildMethodFactory<C> {

    private final ClassBuilder<C> bldr;
    private final BuilderDescriptor desc;
    private final UnsetCheckerFactory<C> checkers;
    private final Function<? super FieldDescriptor, ? extends LocalFieldFactory.LocalFieldGenerator> fields;

    BuildMethodFactory(ClassBuilder<C> bldr, BuilderDescriptor desc,
            UnsetCheckerFactory<C> checkers, Function<? super FieldDescriptor, ? extends LocalFieldFactory.LocalFieldGenerator> fields) {
        this.bldr = bldr;
        this.desc = desc;
        this.checkers = checkers;
        this.fields = fields;
    }

    BuildMethodGenerator flatBuildGenerator() {
        return new FlatBuilderMethodGenerator();
    }

    public interface BuildMethodGenerator {

        void generate();
    }

    private List<FieldDescriptor> descriptorsWithConstraints() {
        Set<FieldDescriptor> descsWithConstraints = new TreeSet<>();
        for (FieldDescriptor fd : desc.fields()) {
            if (!fd.constraints.isEmpty()) {
                descsWithConstraints.add(fd);
            }
        }
        List<FieldDescriptor> fds = new ArrayList<>(descsWithConstraints);
        Collections.sort(fds, (a, b) -> {
            return Integer.compare(a.constraintWeightSum(), b.constraintWeightSum());
        });
        return fds;
    }

    class FlatBuilderMethodGenerator implements BuildMethodGenerator {

        @Override
        public void generate() {
            bldr.method("build", buildMethod -> {
                if (!desc.styles.contains(BuilderStyles.PACKAGE_PRIVATE)) {
                    buildMethod.withModifier(Modifier.PUBLIC);
                }
                if (!desc.thrownTypes().isEmpty()) {
                    for (TypeMirror tm : desc.thrownTypes()) {
                        buildMethod.throwing(tm.toString());
                    }
                }
                String against = bldr.unusedFieldName("against");
                if (desc.instanceType != null) {
                    buildMethod.addArgument(desc.instanceType.asType().toString(), against);
                }

                String generics = desc.fullTargetGenerics();
                buildMethod.returning(desc.targetTypeName + generics);
                buildMethod.annotatedWith("SuppressWarnings")
                        .addArgument("value", "null").closeAnnotation();

                buildMethod.body(bb -> {

                    if (desc.instanceType != null) {
                        bb.ifNull(against)
                                .andThrow(nb -> {
                                    nb.withStringConcatentationArgument("Target instance of ")
                                            .append(desc.instanceType.asType().toString())
                                            .append(" may not be null")
                                            .endConcatenation()
                                            .ofType("IllegalArgumentException");
                                }).endIf();
                    }

                    applyConstraints(bb);

                    if (desc.instanceType != null) {
                        bb.returningInvocationOf(desc.origin.getSimpleName().toString(), ib -> {
                            for (FieldDescriptor fd : desc.paramForVar.values()) {
                                LocalFieldGenerator loc = fields.apply(fd);

//                                ib.withArgument(loc.localFieldName());
                                fd.applyParam(loc.localFieldName(), checkers.generatorFor(fd), ib, bldr);
                            }
                            ib.on(against);
                        });
                    } else {
                        bb.returningNew(nb -> {
                            for (FieldDescriptor fd : desc.paramForVar.values()) {
                                LocalFieldGenerator loc = fields.apply(fd);
                                fd.applyParam(loc.localFieldName(), checkers.generatorFor(fd), nb, bldr);
//                                nb.withArgument(loc.localFieldName());
                            }
                            nb.ofType(desc.targetTypeName + (generics.isEmpty() ? "" : "<>"));
                        });
                    }
                });
            });
        }

        void applyConstraints(ClassBuilder.BlockBuilder<?> bb) {
            List<FieldDescriptor> descriptorsWithConstraints = descriptorsWithConstraints();
            Set<FieldDescriptor> requiredDescriptors = desc.requiredFields();

            int possibleProblemProducers = descriptorsWithConstraints.size()
                    + requiredDescriptors.size();

            if (possibleProblemProducers > 0) {
                String probs = desc.uniquify("problems");
                bb.declare(probs)
                        .initializedWithNew(nb -> {
                            nb.withArgument(Math.max(descriptorsWithConstraints.size(), requiredDescriptors.size()))
                                    .ofType("java.util.ArrayList<>");
                        })
                        .as("java.util.List<String>");
                checkers.generateAllChecks(bb, probs, "add");

                if (!descriptorsWithConstraints.isEmpty()) {
                    bb.lineComment("If there are some nulls, we will fail anyway - do not");
                    bb.lineComment("attempt to run constraints.");
                    ClassBuilder.IfBuilder<?> iff = bb.iff().invoke("isEmpty").on(probs).isTrue().endCondition();
                    for (FieldDescriptor fd : descriptorsWithConstraints) {
                        applyConstraintSet(bldr, desc, fd, iff, probs, fields.apply(fd).localFieldName());
                    }
                    iff.endIf();
                }

                bb.iff().invoke("isEmpty").on(probs).isFalse().endCondition(ib -> {
                    String sb = bldr.unusedFieldName("message");
                    ib.declare(sb).initializedWithNew(nb -> {
                        nb.withArgument(probs + ".size() * 40")
                                .ofType("StringBuilder");
                    }).as("StringBuilder");
                    String loopVar = bldr.unusedFieldName("oneProblem");
                    ib.simpleLoop("String", loopVar, loop -> {
                        loop.over(probs, loopBody -> {
                            loopBody.iff().booleanExpression(sb + ".length() > 0")
                                    .invoke("append").withArgument('\n')
                                    .on(sb).endIf();
                            loopBody.invoke("append").withArgument(loopVar).on(sb);
                        });
                    });
                    ib.andThrow(nb -> {
                        nb.withArgumentFromInvoking("toString").on(sb)
                                .ofType("IllegalStateException");
                    }).endIf();
                });
            }
        }

    }

    static <C, T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X>
            void applyConstraintSet(ClassBuilder<C> bldr, BuilderDescriptor desc, FieldDescriptor fd, B iff, String problemsList, String localFieldName) {
        List<ConstraintGenerator> cgs = fd.constraintsSorted();
        boolean haveHeavy = false;
        for (ConstraintGenerator cg : cgs) {
            if (cg.weight() >= 500) {
                haveHeavy = true;
                continue;
            }
            // XXX move this
            cg.decorateClass(bldr.topLevel());
            iff.lineComment("Weight " + cg.weight() + " " + cg.getClass().getSimpleName());
            cg.generate(localFieldName, problemsList, "add", desc.utils(), iff, fd.fieldName);
        }
        if (haveHeavy) {
            iff.lineComment("Very heavyweight constraints that loop over collections or arrays");
            iff.lineComment("run last, and only if no other constraint has already failed.");
            ClassBuilder.IfBuilder<?> if2 = iff.iff().invokeAsBoolean("isEmpty").on(problemsList);
            for (ConstraintGenerator cg : cgs) {
                if (cg.weight() < 500) {
                    continue;
                }
                // XXX move this
                cg.decorateClass(bldr.topLevel());
                iff.lineComment("Weight " + cg.weight() + " " + cg.getClass().getSimpleName());
                cg.generate(localFieldName, problemsList, "add", desc.utils(), if2, fd.fieldName);
            };
            if2.endIf();
        }
    }
}
