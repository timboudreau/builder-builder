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
import com.mastfrog.util.service.ServiceProvider;
import java.util.function.Consumer;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(ConstraintHandler.class)
public class IntMinMaxHandler implements ConstraintHandler {

    private static final String INT_MIN = "com.mastfrog.builder.annotations.constraint.IntMin";
    private static final String INT_MAX = "com.mastfrog.builder.annotations.constraint.IntMax";

    @Override
    public void collect(AnnotationUtils utils, Element targetElement, VariableElement parameterElement,
            Consumer<ConstraintGenerator> genConsumer) {
        AnnotationMirror min = utils.findAnnotationMirror(parameterElement, INT_MIN);
        AnnotationMirror max = utils.findAnnotationMirror(parameterElement, INT_MAX);
        boolean nullable = utils.findAnnotationMirror(parameterElement, NULLABLE_ANNOTATION) != null;
        if (min != null || max != null) {
            TypeMirror paramType = parameterElement.asType();
            if ("int[]".equals(paramType.toString())) {
                genConsumer.accept(new IntArrayMinMaxGenerator(utils, min, max, nullable));
                return;
            }
            if (!utils.isAssignable(paramType, Integer.class.getName()) && !utils.isAssignable(paramType, int.class.getName())) {
                utils.fail("Cannot apply IntMin or IntMax to a " + paramType, parameterElement, (min == null ? min : max));
                return;
            }
        }
        if (min != null) {
            genConsumer.accept(new IntMinGenerator(utils, min, nullable));
        }
        if (max != null) {
            genConsumer.accept(new IntMaxGenerator(utils, max, nullable));
        }
    }

    public boolean equals(Object o) {
        return o instanceof IntMinMaxHandler;
    }

    public int hashCode() {
        return IntMinMaxHandler.class.hashCode();
    }

    private static class IntMaxGenerator implements ConstraintGenerator {

        private final int max;
        private final boolean nullable;

        IntMaxGenerator(AnnotationUtils utils, AnnotationMirror max, boolean nullable) {
            this.max = utils.annotationValue(max, "value", Integer.class, Integer.MAX_VALUE);
            this.nullable = nullable;
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String fieldVariableName, String problemsListVariableName, String addMethodName,
                AnnotationUtils utils, B bb, String parameterName) {
            if (nullable) {
                ClassBuilder.IfBuilder<B> iff = bb.ifNotNull(fieldVariableName);
                apply(fieldVariableName, problemsListVariableName, addMethodName, utils, iff, parameterName);
                iff.endIf();
            } else {
                apply(fieldVariableName, problemsListVariableName, addMethodName, utils, bb, parameterName);
            }
        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void apply(String fieldVariableName,
                String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb,
                String parameterName) {
            bb.iff().value().expression(fieldVariableName).isGreaterThan(max)
                    .invoke(addMethodName)
                    .withStringConcatentationArgument(parameterName)
                    .append(" must be less than or equal to ").append(max)
                    .append(" but is ").appendExpression(fieldVariableName)
                    .endConcatenation().on(problemsListVariableName)
                    .endIf();
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            bulletPoints.accept("value must be &lt;= <code>" + max + "</code>");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + max + " nullable " + nullable + ")";
        }
    }

    private static class IntMinGenerator implements ConstraintGenerator {

        private final int min;
        private final boolean nullable;

        IntMinGenerator(AnnotationUtils utils, AnnotationMirror min, boolean nullable) {
            this.min = utils.annotationValue(min, "value", Integer.class, Integer.MIN_VALUE);
            this.nullable = nullable;
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String fieldVariableName, String problemsListVariableName, String addMethodName,
                AnnotationUtils utils, B bb, String parameterName) {
            if (nullable) {
                ClassBuilder.IfBuilder<B> iff = bb.ifNotNull(fieldVariableName);
                apply(fieldVariableName, problemsListVariableName, addMethodName, utils, iff, parameterName);
                iff.endIf();
            } else {
                apply(fieldVariableName, problemsListVariableName, addMethodName, utils, bb, parameterName);
            }
        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void apply(String fieldVariableName,
                String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb,
                String parameterName) {
            bb.iff().value()
                    .expression(fieldVariableName)
                    .isLessThan(min)
                    .invoke(addMethodName)
                    .withStringConcatentationArgument(parameterName)
                    .append(" must be greater than or equal to ")
                    .append(min)
                    .append(" but is ").appendExpression(fieldVariableName)
                    .endConcatenation()
                    .on(problemsListVariableName)
                    .endIf();
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            bulletPoints.accept("value must be &gt;= <code>" + min + "</code>");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + min + " nullable " + nullable + ")";
        }
    }

    private static final class IntArrayMinMaxGenerator implements ConstraintGenerator {

        private final int min;
        private final int max;
        private final boolean nullable;

        IntArrayMinMaxGenerator(AnnotationUtils utils, AnnotationMirror min, AnnotationMirror max, boolean nullable) {
            this.min = min == null ? Integer.MIN_VALUE : utils.annotationValue(min, "value", Integer.class, Integer.MIN_VALUE);
            this.max = max == null ? Integer.MAX_VALUE : utils.annotationValue(max, "value", Integer.class, Integer.MAX_VALUE);
            this.nullable = nullable;
        }

        public String toString() {
            return getClass().getSimpleName() + "(min " + min + " max " + max + " nullable " + nullable + ")";
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String localFieldName, String problemsListVariableName, String addMethodName,
                AnnotationUtils utils, B bb, String parameterName) {
            if (nullable) {
                ClassBuilder.IfBuilder<B> iff = bb.iff().isNotNull(localFieldName).endCondition();
                apply(localFieldName, problemsListVariableName, addMethodName, utils, iff, parameterName);
                iff.endIf();
            } else {
                apply(localFieldName, problemsListVariableName, addMethodName, utils, bb, parameterName);
            }
        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void apply(String localFieldName,
                String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb,
                String parameterName) {
            String v = "_i";
            bb.forVar(v, fv -> {
                fv.initializedWith(0).condition().lessThan().field("length").of(localFieldName).endCondition().running(forBlock -> {
                    if (min != Integer.MIN_VALUE) {
                        forBlock.iff().variable(localFieldName + "[" + v + "]").isLessThan(min)
                                .invoke(addMethodName)
                                .withStringConcatentationArgument("int[] param", scb -> {
                                    scb.append(parameterName)
                                            .append("' at index ")
                                            .appendExpression(v)
                                            .append(" must be >= ")
                                            .append(min)
                                            .append(" but is ")
                                            .appendExpression(localFieldName + "[" + v + "]");

                                })
                                .on(problemsListVariableName)
                                .breaking()
                                .endIf();
                    }
                    if (max != Integer.MIN_VALUE) {
                        forBlock.iff().variable(localFieldName + "[" + v + "]").isGreaterThan(max)
                                .invoke(addMethodName)
                                .withStringConcatentationArgument("int[] param '")
                                .append(parameterName)
                                .append("' at index ")
                                .appendExpression(v)
                                .append(" must be <= ")
                                .append(max)
                                .append(" but is ")
                                .appendExpression(localFieldName + "[" + v + "]")
                                .endConcatenation()
                                .on(problemsListVariableName)
                                .statement("break")
                                .endIf();

                    }
                });
            });
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            if (nullable) {
                bulletPoints.accept("Parameter is optional.");
            }
            bulletPoints.accept("All values must be >= " + min);
            bulletPoints.accept("All values must be <= " + max);
        }

        @Override
        public int weight() {
            return 500;
        }
    }
}
