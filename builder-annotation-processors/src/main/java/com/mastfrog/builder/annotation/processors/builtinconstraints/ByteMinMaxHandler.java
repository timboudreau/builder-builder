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
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
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
public class ByteMinMaxHandler implements ConstraintHandler {

    private static final String BYTE_MIN = "com.mastfrog.builder.annotations.constraint.ByteMin";
    private static final String BYTE_MAX = "com.mastfrog.builder.annotations.constraint.ByteMax";

    @Override
    public void collect(AnnotationUtils utils, Element targetElement, VariableElement parameterElement,
            Consumer<ConstraintGenerator> genConsumer) {
        AnnotationMirror min = utils.findAnnotationMirror(parameterElement, BYTE_MIN);
        AnnotationMirror max = utils.findAnnotationMirror(parameterElement, BYTE_MAX);
        boolean nullable = utils.findAnnotationMirror(parameterElement, NULLABLE_ANNOTATION) != null;
        if (min != null || max != null) {
            TypeMirror paramType = parameterElement.asType();
            boolean isBoxedByte = utils.isAssignable(paramType, Byte.class.getName());
            boolean isPrimitiveByte = !isBoxedByte && utils.isAssignable(paramType, byte.class.getName());
            boolean isNumber = !isBoxedByte && !isPrimitiveByte
                    && utils.isAssignable(paramType, Number.class.getName());

            if (!isBoxedByte && !isPrimitiveByte && !isNumber) {
                utils.fail("Cannot apply ByteMin or ByteMax to a " + paramType, parameterElement, (min == null ? min : max));
                return;
            }
            if (min != null) {
                genConsumer.accept(new ByteMinGenerator(utils, min, nullable, isNumber));
            }
            if (max != null) {
                genConsumer.accept(new ByteMaxGenerator(utils, max, nullable, isNumber));
            }
        }
    }

    public boolean equals(Object o) {
        return o instanceof ShortMinMaxHandler;
    }

    public int hashCode() {
        return ShortMinMaxHandler.class.hashCode();
    }

    private static class ByteMaxGenerator implements ConstraintGenerator {

        private final byte max;
        private final boolean nullable;
        private final boolean isNumber;

        ByteMaxGenerator(AnnotationUtils utils, AnnotationMirror max, boolean nullable, boolean isNumber) {
            this.max = utils.annotationValue(max, "value", Byte.class, Byte.MAX_VALUE);
            this.nullable = nullable;
            this.isNumber = isNumber;
        }

        @Override
        public <T, B extends BlockBuilderBase<T, B, X>, X> void
                generate(String fieldVariableName, String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            if (nullable) {
                IfBuilder<B> test = bb.ifNotNull(fieldVariableName);
                doGenerate(fieldVariableName, problemsListVariableName, addMethodName,
                        utils, test, parameterName);
                test.endIf();
            } else {
                doGenerate(fieldVariableName, problemsListVariableName, addMethodName,
                        utils, bb, parameterName);
            }
        }

        private <T, B extends BlockBuilderBase<T, B, X>, X> void
                doGenerate(String fieldVariableName, String problemsListVariableName,
                        String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            ComparisonBuilder<IfBuilder<B>> test;
            if (isNumber) {
                test = bb.iff().value().invoke("byteValue").on(fieldVariableName);
            } else {
                test = bb.iff().value().expression(fieldVariableName);
            }

            test.isGreaterThan(max)
                    .invoke(addMethodName)
                    .withStringConcatentationArgument(fieldVariableName)
                    .append(" must be less than or equal to ")
                    .append(max)
                    .append(" but is ").appendExpression(fieldVariableName)
                    .endConcatenation()
                    .on(problemsListVariableName)
                    .endIf();
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            bulletPoints.accept("value must be &lt;= <code>" + max + "</code>");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + max + /* " nullable " + nullable + */ ")";
        }
    }

    private static class ByteMinGenerator implements ConstraintGenerator {

        private final int min;
        private final boolean nullable;
        private final boolean isNumber;

        ByteMinGenerator(AnnotationUtils utils, AnnotationMirror min, boolean nullable, boolean isNumber) {
            this.min = utils.annotationValue(min, "value", Byte.class, Byte.MIN_VALUE);
            this.nullable = nullable;
            this.isNumber = isNumber;
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            bulletPoints.accept("value must be &gt;= <code>" + min + "</code>");
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String fieldVariableName, String problemsListVariableName,
                String addMethodName, AnnotationUtils utils, B bb, String parameterName) {

            bb.lineComment(getClass().getName());
            if (nullable) {
                IfBuilder<B> test = bb.ifNotNull(fieldVariableName);
                doGenerate(fieldVariableName, problemsListVariableName, addMethodName,
                        utils, test, parameterName);
                test.endIf();
            } else {
                doGenerate(fieldVariableName, problemsListVariableName, addMethodName,
                        utils, bb, parameterName);
            }

        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void doGenerate(
                String fieldVariableName, String problemsListVariableName,
                String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            ComparisonBuilder<IfBuilder<B>> test;
            if (isNumber) {
                test = bb.iff().value().invoke("byteValue").on(fieldVariableName);
            } else {
                test = bb.iff().value().expression(fieldVariableName);
            }

            test.isLessThan(min)
                    .invoke(addMethodName)
                    .withStringConcatentationArgument(fieldVariableName)
                    .append(" must be less than or equal to ")
                    .append(min)
                    .append(" but is ").appendExpression(fieldVariableName)
                    .endConcatenation()
                    .on(problemsListVariableName)
                    .endIf();
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "(" + min + /* " nullable " + nullable + */ ")";
        }
    }
}
