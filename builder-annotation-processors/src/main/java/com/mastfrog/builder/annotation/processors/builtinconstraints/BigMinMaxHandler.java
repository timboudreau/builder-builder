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
import com.mastfrog.java.vogon.ClassBuilder.Value;
import com.mastfrog.java.vogon.ClassBuilder.ValueExpressionBuilder;
import com.mastfrog.java.vogon.ClassBuilder.Variable;
import static com.mastfrog.java.vogon.ClassBuilder.number;
import com.mastfrog.util.service.ServiceProvider;
import java.math.BigDecimal;
import java.math.BigInteger;
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
public final class BigMinMaxHandler implements ConstraintHandler {

    private static final String BIG_MIN = "com.mastfrog.builder.annotations.constraint.BigMin";
    private static final String BIG_MAX = "com.mastfrog.builder.annotations.constraint.BigMax";

    @Override
    public void collect(AnnotationUtils utils, Element targetElement,
            VariableElement parameterElement, Consumer<ConstraintGenerator> genConsumer) {
        AnnotationMirror min = utils.findAnnotationMirror(parameterElement, BIG_MIN);
        AnnotationMirror max = utils.findAnnotationMirror(parameterElement, BIG_MAX);
        boolean nullable = utils.findAnnotationMirror(parameterElement,
                NULLABLE_ANNOTATION) != null;
        if (min != null || max != null) {
            TypeMirror paramType = parameterElement.asType();
            boolean isBigInteger = utils.isAssignable(paramType, BigInteger.class.getName());
            boolean isBigDecimal = utils.isAssignable(paramType, BigDecimal.class.getName());
            if (!isBigInteger && !isBigDecimal) {
                utils.fail("Cannot apply BigMin or BigMax to a " + paramType,
                        parameterElement, (min == null ? min : max));
                return;
            }
            if (min != null) {
                genConsumer.accept(new BigMinGenerator(utils, min, nullable, isBigInteger ? "BigInteger" : "BigDecimal"));
            }
            if (max != null) {
                genConsumer.accept(new BigMaxGenerator(utils, max, nullable, isBigInteger ? "BigInteger" : "BigDecimal"));
            }
        }

    }

    private static class BigMinGenerator implements ConstraintGenerator {

        private final AnnotationUtils utils;
        private final AnnotationMirror min;
        private final boolean nullable;
        private final String typeName;

        private BigMinGenerator(AnnotationUtils utils, AnnotationMirror min, boolean nullable,
                String typeName) {
            this.utils = utils;
            this.min = min;
            this.nullable = nullable;
            this.typeName = typeName;
        }

        @Override
        public <C> void decorateClass(ClassBuilder<C> bldr) {
            bldr.importing("java.math." + typeName);
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            bulletPoints.accept("value must be >= " + utils.annotationValue(min, "value", String.class));
        }

        @Override
        public <T, B extends BlockBuilderBase<T, B, X>, X> void generate(
                String fieldVariableName, String problemsListVariableName, String addMethodName,
                AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            String stringLit = utils.annotationValue(min, "value", String.class);
            Value v = ClassBuilder.invocationOf("compareTo")
                    .withArgument(fieldVariableName)
                    .onNew(nb -> {
                        nb.withStringLiteral(stringLit)
                                .ofType(typeName);
                    }).isGreaterThan(number(0));

            if (nullable) {
                v = ClassBuilder.invocationOf("_isSet")
                        .withArgument(fieldVariableName)
                        .on("this")
                        .logicalAndWith(v);
            }

            bb.iff(v)
                    .invoke(addMethodName)
                    .withStringConcatentationArgument(parameterName)
                    .append(" must be greater than or equal to ")
                    .append(stringLit)
                    .append(" but is ").appendExpression(fieldVariableName)
                    .endConcatenation()
                    .on(problemsListVariableName)
                    .endIf();
        }

    }

    private static class BigMaxGenerator implements ConstraintGenerator {

        private final AnnotationUtils utils;
        private final AnnotationMirror min;
        private final boolean nullable;
        private final String typeName;

        private BigMaxGenerator(AnnotationUtils utils, AnnotationMirror min, boolean nullable,
                String typeName) {
            this.utils = utils;
            this.min = min;
            this.nullable = nullable;
            this.typeName = typeName;
        }

        @Override
        public <C> void decorateClass(ClassBuilder<C> bldr) {
            bldr.importing("java.math." + typeName);
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            bulletPoints.accept("value must be <= " + utils.annotationValue(min, "value", String.class));
        }

        @Override
        public <T, B extends BlockBuilderBase<T, B, X>, X> void generate(
                String fieldVariableName, String problemsListVariableName, String addMethodName,
                AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            String stringLit = utils.annotationValue(min, "value", String.class);
            Value v = ClassBuilder.invocationOf("compareTo")
                    .withArgument(fieldVariableName)
                    .onNew(nb -> {
                        nb.withStringLiteral(stringLit)
                                .ofType(typeName);
                    }).isLessThan(number(0));

            if (nullable) {
                v = ClassBuilder.invocationOf("_isSet")
                        .withArgument(fieldVariableName)
                        .on("this")
                        .logicalAndWith(v);
            }

            bb.iff(v)
                    .invoke(addMethodName)
                    .withStringConcatentationArgument(parameterName)
                    .append(" must be greater than or equal to ")
                    .append(stringLit)
                    .append(" but is ").appendExpression(fieldVariableName)
                    .endConcatenation()
                    .on(problemsListVariableName)
                    .endIf();
        }
    }

}
