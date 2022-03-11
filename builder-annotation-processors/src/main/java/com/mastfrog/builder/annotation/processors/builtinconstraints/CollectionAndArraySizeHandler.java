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
package com.mastfrog.builder.annotation.processors.builtinconstraints;

import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import static com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator.NULLABLE_ANNOTATION;
import com.mastfrog.builder.annotation.processors.spi.ConstraintHandler;
import com.mastfrog.java.vogon.ClassBuilder;
import static java.lang.ProcessBuilder.Redirect.to;
import java.util.function.Consumer;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
public class CollectionAndArraySizeHandler implements ConstraintHandler {

    @Override
    public void collect(AnnotationUtils utils, Element targetElement, VariableElement parameterElement, Consumer<ConstraintGenerator> genConsumer) {
        AnnotationMirror mir = utils.findAnnotationMirror(parameterElement,
                "com.mastfrog.builder.annotations.constraint.CollectionConstraint");
        if (mir == null) {
            System.out.println("NO MIRROR FOR " + parameterElement.getSimpleName() + " " + parameterElement.asType());
            return;
        }
        TypeMirror type = utils.erasureOf(parameterElement.asType());
        int min = utils.annotationValue(mir, "minSize", Integer.class, 0);
        if (min < 0) {
            utils.fail("Minimum < 0: " + min, parameterElement, mir);
        }
        int max = utils.annotationValue(mir, "maxSize", Integer.class, 0);
        if (max < 0) {
            utils.fail("Maximum < 0: " + max, parameterElement, mir);
            return;
        }
        if (max < min) {
            utils.fail("Maximum " + max + " is less than minimum " + min, parameterElement, mir);
            return;
        }

        boolean noNulls = utils.annotationValue(mir, "forbidNullValues", Boolean.class, false);
        boolean nullable = utils.findAnnotationMirror(parameterElement, NULLABLE_ANNOTATION) != null;
        TypeMirror checkedAs = utils.typeForSingleClassAnnotationMember(mir, "checked");
        if (checkedAs != null) {
            checkedAs = utils.erasureOf(checkedAs);
        }
        if (utils.isAssignable(type, "java.util.Map")) {
            if (noNulls) {
                utils.fail("Cannot null check a map's elements", parameterElement, mir);
                return;
            }
            genConsumer.accept(new CollectionConstraintGenerator(min, max, false, nullable, false, checkedAs));
        } else if (utils.isAssignable(type, "java.util.List")) {
            genConsumer.accept(new CollectionConstraintGenerator(min, max, noNulls, nullable, true, checkedAs));
        } else if (utils.isAssignable(type, "java.util.Collection")) {
            genConsumer.accept(new CollectionConstraintGenerator(min, max, noNulls, nullable, false, checkedAs));
        } else if (utils.isAssignable(type, "java.util.Map")) {
            genConsumer.accept(new CollectionConstraintGenerator(min, max, noNulls, nullable, false, checkedAs));
        } else if (type.getKind() == TypeKind.ARRAY) {
            ArrayType at = (ArrayType) type;
            boolean primitive = at.getComponentType().getKind().isPrimitive();
            genConsumer.accept(new ArrayConstraintGenerator(min, max, noNulls, nullable, primitive, checkedAs));
        } else {
            utils.fail("Cannot apply collection constraint to a " + type);
        }
    }

    private static abstract class AbstractGenerator implements ConstraintGenerator {

        final int min;
        final int max;
        final boolean noNullElements;
        final boolean nullableValue;
        final boolean isListOrPrimitive;
        final TypeMirror checkedAs;

        public AbstractGenerator(int min, int max, boolean noNullElements,
                boolean nullableValue, boolean isListOrPrimitive, TypeMirror checked) {
            this.min = min;
            this.max = max;
            this.noNullElements = noNullElements;
            this.nullableValue = nullableValue;
            this.isListOrPrimitive = isListOrPrimitive;
            this.checkedAs = checked;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" + "min=" + min + ", max="
                    + max + ", noNullElements=" + noNullElements
                    + ", nullableValue=" + nullableValue + '}';
        }
    }

    private static final class ArrayConstraintGenerator extends AbstractGenerator {

        public ArrayConstraintGenerator(int min, int max, boolean noNullElements, boolean nullableValue, boolean isList, TypeMirror checkedAs) {
            super(min, max, noNullElements, nullableValue, isList, checkedAs);
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(String fieldVariableName, String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb) {
            if (nullableValue) {
                ClassBuilder.IfBuilder<B> iff = bb.ifNotNull(addMethodName);
                applyConstraints(fieldVariableName, problemsListVariableName, addMethodName, iff);
                iff.endIf();
            } else {
                applyConstraints(fieldVariableName, problemsListVariableName, addMethodName, bb);
            }
        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void applyConstraints(
                String fieldVariableName,
                String problemsListVariableName, String addMethodName, B bb) {
            if (min > 0) {
                bb.iff().variable(fieldVariableName + ".length").isLessThan(min)
                        .invoke(addMethodName)
                        .withStringConcatentationArgument(fieldVariableName)
                        .append(" must be >= ").append(min)
                        .append(", but is ")
                        .appendExpression(fieldVariableName + ".length")
                        .append(": ")
                        .appendInvocationOf("toString").withArgument(fieldVariableName).on("java.util.Arrays")
                        .endConcatenation()
                        .on(problemsListVariableName)
                        .endIf();
            }
            if (max < Integer.MAX_VALUE) {
                bb.iff().variable(fieldVariableName + ".length").isGreaterThan(max)
                        .invoke(addMethodName)
                        .withStringConcatentationArgument(fieldVariableName)
                        .append(" must be <= ").append(max)
                        .append(", but is ")
                        .appendExpression(fieldVariableName + ".length")
                        .append(": ")
                        .appendInvocationOf("toString").withArgument(fieldVariableName).on("java.util.Arrays")
                        .endConcatenation()
                        .on(problemsListVariableName)
                        .endIf();
            }
            boolean check = (checkedAs != null && !"java.lang.Object".equals(checkedAs.toString()));
            if (!isListOrPrimitive && (noNullElements || check)) {
                bb.forVar("_i", fv -> {
                    ClassBuilder.BlockBuilder<?> blk = fv.initializedWith(0).condition().lessThan().expression(fieldVariableName + ".length")
                            .endCondition().running();
                    if (noNullElements) {
                        blk.ifNull(fieldVariableName + "[_i]")
                                .invoke(addMethodName)
                                .withStringConcatentationArgument(fieldVariableName)
                                .append(" should not contain null elements, but does at index ")
                                .appendExpression(("_i")).append(": ")
                                .appendInvocationOf("toString").withArgument(fieldVariableName).on("java.util.Arrays")
                                .endConcatenation()
                                .on(problemsListVariableName)
                                .statement("break")
                                .endIf();
                    }
                    if (check) {
                        blk.ifNotNull(fieldVariableName + "[_i]")
                                .iff().invoke("isInstance")
                                .withArgument(fieldVariableName + "[_i]")
                                .on(checkedAs + ".class")
                                .isFalse()
                                .endCondition()
                                .invoke(addMethodName)
                                .withStringConcatentationArgument(fieldVariableName)
                                .append(" should contain instances of ")
                                .append(checkedAs.toString())
                                .append(" but element at index ").appendExpression("_i")
                                .append(" is a ").appendInvocationOf("getClass").on(fieldVariableName + "[_i]")
                                .append(" in ")
                                .appendInvocationOf("toString").withArgument(fieldVariableName).on("java.util.Arrays")
                                .endConcatenation()
                                .on(problemsListVariableName)
                                .statement("break")
                                .endIf()
                                .endIf();
                    }
                    blk.endBlock();
                });
            }
        }
    }

    private static final class CollectionConstraintGenerator extends AbstractGenerator {

        public CollectionConstraintGenerator(int min, int max, boolean noNullElements,
                boolean nullableValue, boolean isList, TypeMirror checkedAs) {
            super(min, max, noNullElements, nullableValue, isList, checkedAs);
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String fieldVariableName, String problemsListVariableName, String addMethodName,
                AnnotationUtils utils, B bb) {
            if (nullableValue) {
                ClassBuilder.IfBuilder<B> iff = bb.ifNotNull(addMethodName);
                applyConstraints(fieldVariableName, problemsListVariableName, addMethodName, iff);
                iff.endIf();
            } else {
                applyConstraints(fieldVariableName, problemsListVariableName, addMethodName, bb);
            }
        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void applyConstraints(
                String fieldVariableName,
                String problemsListVariableName, String addMethodName, B bb) {
            if (min > 0) {
                bb.iff().variable(fieldVariableName + ".size()").isLessThan(min)
                        .invoke(addMethodName)
                        .withStringConcatentationArgument(fieldVariableName)
                        .append(" must be >= ").append(min)
                        .append(", but is ")
                        .appendExpression(fieldVariableName + ".size()")
                        .endConcatenation()
                        .on(problemsListVariableName)
                        .endIf();
            }
            if (max < Integer.MAX_VALUE) {
                bb.iff().variable(fieldVariableName + ".size()").isGreaterThan(max)
                        .invoke(addMethodName)
                        .withStringConcatentationArgument(fieldVariableName)
                        .append(" must be <= ").append(max)
                        .append(", but is ")
                        .appendExpression(fieldVariableName + ".size()")
                        .endConcatenation()
                        .on(problemsListVariableName)
                        .endIf();
            }
            if (noNullElements) {
                if (isListOrPrimitive) {
                    bb.forVar("_i", fv -> {
                        fv.initializedWith(0).condition().lessThan().expression(fieldVariableName + ".size()")
                                .endCondition().running()
                                .ifNull(fieldVariableName + ".get(" + "_i" + ")")
                                .invoke(addMethodName)
                                .withStringConcatentationArgument(fieldVariableName)
                                .append(" should not contain null elements, but does at index ")
                                .appendExpression(("_i")).append(": ")
                                .appendExpression(fieldVariableName).endConcatenation()
                                .on(problemsListVariableName)
                                .statement("break")
                                .endIf()
                                .endBlock();
                    });
                } else {
                    String vn = "_" + fieldVariableName + "Item";
                    bb.simpleLoop("Object", vn, loop -> loop.over(fieldVariableName, bk -> {
                        bk.ifNull(vn).invoke(addMethodName)
                                .withStringConcatentationArgument(fieldVariableName)
                                .append(" should not contain null elements: ")
                                .appendExpression(fieldVariableName)
                                .endConcatenation().on(problemsListVariableName)
                                .statement("break")
                                .endIf();
                    }));
                }
            }
        }
    }
}
