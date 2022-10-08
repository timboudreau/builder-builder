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
public class ShortMinMaxHandler implements ConstraintHandler {

    private static final String SHORT_MIN = "com.mastfrog.builder.annotations.constraint.ShortMin";
    private static final String SHORT_MAX = "com.mastfrog.builder.annotations.constraint.ShortMax";

    @Override
    public void collect(AnnotationUtils utils, Element targetElement, VariableElement parameterElement,
            Consumer<ConstraintGenerator> genConsumer) {
        AnnotationMirror min = utils.findAnnotationMirror(parameterElement, SHORT_MIN);
        AnnotationMirror max = utils.findAnnotationMirror(parameterElement, SHORT_MAX);
        boolean nullable = utils.findAnnotationMirror(parameterElement, NULLABLE_ANNOTATION) != null;
        if (min != null || max != null) {
            TypeMirror paramType = parameterElement.asType();
            boolean isBoxedShort = utils.isAssignable(paramType, Short.class.getName());
            boolean isPrimitiveShort = !isBoxedShort &&
                    utils.isAssignable(paramType, short.class.getName());
            boolean isNumber = !isBoxedShort && !isPrimitiveShort
                    && utils.isAssignable(paramType, Number.class.getName());
            if (!isBoxedShort && !isPrimitiveShort && !isNumber) {
                utils.fail("Cannot apply ShortMin or ShortMax to a " + paramType, parameterElement, (min == null ? min : max));
                return;
            }
            if (min != null) {
                genConsumer.accept(new ShortMinGenerator(utils, min, isNumber, nullable));
            }
            if (max != null) {
                genConsumer.accept(new ShortMaxGenerator(utils, max, isNumber, nullable));
            }
        }
    }

    public boolean equals(Object o) {
        return o instanceof ShortMinMaxHandler;
    }

    public int hashCode() {
        return ShortMinMaxHandler.class.hashCode();
    }

    private static class ShortMaxGenerator implements ConstraintGenerator {

        private final short max;
        private final boolean isNumber;
        private final boolean nullable;

        ShortMaxGenerator(AnnotationUtils utils, AnnotationMirror max, boolean isNumber, boolean nullable) {
            this.max = utils.annotationValue(max, "value", Short.class, Short.MAX_VALUE);
            this.isNumber = isNumber;
            this.nullable = nullable;
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String localFieldName, String problemsListVariableName,
                String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            if (nullable) {
                IfBuilder<B> iff = bb.iff().isNotNull(localFieldName).endCondition();
                doGenerate(localFieldName, problemsListVariableName, addMethodName,
                        utils, iff, parameterName);
                iff.endIf();
            } else {
                doGenerate(localFieldName, problemsListVariableName, addMethodName,
                        utils, bb, parameterName);
            }
        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void doGenerate(
                String fieldVariableName, String problemsListVariableName,
                String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            ComparisonBuilder<IfBuilder<B>> test;
            if (isNumber) {
                test = bb.iff().value()
                        .invoke("shortValue")
                        .on(fieldVariableName);
            } else {
                test = bb.iff().value()
                        .expression(fieldVariableName);
            }

            test.isGreaterThan(max)
                    .invoke(addMethodName)
                    .withStringConcatentationArgument(parameterName)
                    .append(" must be less than or equal to ")
                    .append(max)
                    .append(" but is ").appendExpression(fieldVariableName)
                    .endConcatenation().on(problemsListVariableName)
                    .endIf();
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            bulletPoints.accept("value must be &lt;= <code>" + max + "<code>");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + max + /* " nullable " + nullable + */ ")";
        }
    }

    private static class ShortMinGenerator implements ConstraintGenerator {

        private final short min;
        private final boolean isNumber;
        private final boolean nullable;

        ShortMinGenerator(AnnotationUtils utils, AnnotationMirror min, boolean isNumber, boolean nullable) {
            this.min = utils.annotationValue(min, "value", Short.class, Short.MIN_VALUE);
            this.isNumber = isNumber;
            this.nullable = nullable;
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String localFieldName, String problemsListVariableName,
                String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            if (nullable) {
                IfBuilder<B> iff = bb.iff().isNotNull(localFieldName).endCondition();
                doGenerate(localFieldName, problemsListVariableName, addMethodName,
                        utils, iff, parameterName);
                iff.endIf();
            } else {
                doGenerate(localFieldName, problemsListVariableName, addMethodName,
                        utils, bb, parameterName);
            }
        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void doGenerate(
                String fieldVariableName, String problemsListVariableName,
                String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            ComparisonBuilder<IfBuilder<B>> test;
            if (isNumber) {
                test = bb.iff().value()
                        .invoke("shortValue")
                        .on(fieldVariableName);
            } else {
                test = bb.iff().value()
                        .expression(fieldVariableName);
            }

            test
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
            return getClass().getSimpleName() + "(" + min + /* " nullable " + nullable + */ ")";
        }
    }
}
