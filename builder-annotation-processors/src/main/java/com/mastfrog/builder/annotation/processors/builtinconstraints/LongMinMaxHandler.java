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
import com.mastfrog.java.vogon.ClassBuilder.ComparisonBuilder;
import com.mastfrog.java.vogon.ClassBuilder.IfBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ValueExpressionBuilder;
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
public class LongMinMaxHandler implements ConstraintHandler {

    private static final String LONG_MIN = "com.mastfrog.builder.annotations.constraint.LongMin";
    private static final String LONG_MAX = "com.mastfrog.builder.annotations.constraint.LongMax";

    @Override
    public void collect(AnnotationUtils utils, Element targetElement, VariableElement parameterElement,
            Consumer<ConstraintGenerator> genConsumer) {
        AnnotationMirror min = utils.findAnnotationMirror(parameterElement, LONG_MIN);
        AnnotationMirror max = utils.findAnnotationMirror(parameterElement, LONG_MAX);
        boolean nullable = utils.findAnnotationMirror(parameterElement, NULLABLE_ANNOTATION) != null;
        if (min != null || max != null) {
            TypeMirror paramType = parameterElement.asType();
            boolean isBoxedLong = utils.isAssignable(paramType, Long.class.getName());
            boolean isPrimitiveLong = !isBoxedLong && utils.isAssignable(paramType, long.class.getName());
            boolean isNumber = !isBoxedLong && !isPrimitiveLong
                    && utils.isAssignable(paramType, Number.class.getName());
            if (!isNumber && !isBoxedLong && !isPrimitiveLong) {
                utils.fail("Cannot apply IntMin or LongMax to a " + paramType, parameterElement, (min == null ? min : max));
                return;
            }
            if (min != null) {
                genConsumer.accept(new LongMinGenerator(utils, min, nullable, isNumber));
            }
            if (max != null) {
                genConsumer.accept(new LongMaxGenerator(utils, max, nullable, isNumber));
            }
        }
    }

    public boolean equals(Object o) {
        return o instanceof LongMinMaxHandler;
    }

    public int hashCode() {
        return LongMinMaxHandler.class.hashCode();
    }

    private static class LongMaxGenerator implements ConstraintGenerator {

        private final long max;
        private final boolean nullable;
        private final boolean isNumber;

        LongMaxGenerator(AnnotationUtils utils, AnnotationMirror max, boolean nullable, boolean isNumber) {
            this.max = utils.annotationValue(max, "value", Long.class, Long.MAX_VALUE);
            this.nullable = nullable;
            this.isNumber = isNumber;
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

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void apply(
                String fieldVariableName, String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            ComparisonBuilder<IfBuilder<B>> tst;
            if (!isNumber) {
                tst = bb.iff().value().expression(fieldVariableName);
            } else {
                tst = bb.iff().value().invoke("longValue").on(fieldVariableName);
            }
            bb.lineComment("Is number? " + isNumber);
            bb.lineComment("Nullable? " + nullable);

            tst.isGreaterThan(max)
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

    private static class LongMinGenerator implements ConstraintGenerator {

        private final long min;
        private final boolean nullable;
        private final boolean isNumber;

        LongMinGenerator(AnnotationUtils utils, AnnotationMirror min, boolean nullable, boolean isNumber) {
            this.min = utils.annotationValue(min, "value", Long.class, Long.MIN_VALUE);
            this.nullable = nullable;
            this.isNumber = isNumber;
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

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void apply(
                String fieldVariableName, String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            ComparisonBuilder<IfBuilder<B>> tst;
            if (!isNumber) {
                tst = bb.iff().value().expression(fieldVariableName);
            } else {
                tst = bb.iff().value().invoke("longValue").on(fieldVariableName);
            }
            bb.lineComment("Is number? " + isNumber);
            bb.lineComment("Nullable? " + nullable);

            tst.isLessThan(min)
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
}
