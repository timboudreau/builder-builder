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
import java.util.function.Supplier;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

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
            boolean isSupplier = false;
            if (!isBigInteger && !isBigDecimal) {
                if (isBigDecimalSupplier(utils, parameterElement)) {
                    isBigDecimal = true;
                    isSupplier = true;
                } else if (isBigIntegerSupplier(utils, parameterElement)) {
                    isBigInteger = true;
                    isSupplier = true;
                }
            }
            if (!isBigInteger && !isBigDecimal) {
                utils.fail("Cannot apply BigMin or BigMax to a " + paramType,
                        parameterElement, (min == null ? min : max));
                return;
            }
            String type = isBigDecimal ? "BigDecimal" : "BigInteger";
            if (min != null) {
                genConsumer.accept(new BigMinGenerator(utils, min, nullable, type, isSupplier));
            }
            if (max != null) {
                genConsumer.accept(new BigMaxGenerator(utils, max, nullable, type, isSupplier));
            }
        }
    }

    private static TypeMirror supplierType(AnnotationUtils utils, VariableElement parameterElement) {
        boolean isSupplier = utils.isAssignable(utils.erasureOf(parameterElement.asType()), Supplier.class.getName());
        if (!isSupplier) {
            return null;
        }
        TypeElement el = utils.typeElementOfField(parameterElement);
        if (el == null) {
            return null;
        }
        TypeMirror param = utils.getTypeParameter(0, el);
        return param;
    }

    private static boolean isBigDecimalSupplier(AnnotationUtils utils, VariableElement parameterElement) {
        return isSupplierOf(utils, parameterElement, BigDecimal.class);
    }

    private static boolean isBigIntegerSupplier(AnnotationUtils utils, VariableElement parameterElement) {
        return isSupplierOf(utils, parameterElement, BigInteger.class);
    }

    static boolean isSupplierOf(AnnotationUtils utils, VariableElement parameterElement, Class<?> what) {
        Elements elu = utils.processingEnv().getElementUtils();
        TypeElement suppType = elu.getTypeElement(Supplier.class.getName());
        TypeElement bdType = elu.getTypeElement(what.getName());

        Types tu = utils.processingEnv().getTypeUtils();
        DeclaredType theType = tu.getDeclaredType(suppType, bdType.asType());
        return tu.isAssignable(parameterElement.asType(), theType);
    }

    private static class BigMinGenerator implements ConstraintGenerator {

        private final AnnotationUtils utils;
        private final AnnotationMirror min;
        private final boolean nullable;
        private final String typeName;
        private final boolean isSupplier;

        private BigMinGenerator(AnnotationUtils utils, AnnotationMirror min, boolean nullable,
                String typeName, boolean isSupplier) {
            this.utils = utils;
            this.min = min;
            this.nullable = nullable;
            this.typeName = typeName;
            this.isSupplier = isSupplier;
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
            if (nullable) {
                IfBuilder<B> test = bb.ifNotNull(fieldVariableName);
                doGenerate(fieldVariableName, problemsListVariableName, addMethodName, utils, test, parameterName);
                test.endIf();
            } else {
                doGenerate(fieldVariableName, problemsListVariableName, addMethodName, utils, bb, parameterName);
            }
        }

        private <T, B extends BlockBuilderBase<T, B, X>, X> void doGenerate(
                String fieldVariableName, String problemsListVariableName, String addMethodName,
                AnnotationUtils utils, B bb, String parameterName) {
            String stringLit = utils.annotationValue(min, "value", String.class);
            Value v;
            if (isSupplier) {
                v = ClassBuilder.invocationOf("compareTo")
                        .withArgumentFromInvoking("get")
                        .on(fieldVariableName)
                        .onNew(nb -> {
                            nb.withStringLiteral(stringLit)
                                    .ofType(typeName);
                        }).isGreaterThan(number(0));

            } else {
                v = ClassBuilder.invocationOf("compareTo")
                        .withArgument(fieldVariableName)
                        .onNew(nb -> {
                            nb.withStringLiteral(stringLit)
                                    .ofType(typeName);
                        }).isGreaterThan(number(0));
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
        private final boolean isSupplier;

        private BigMaxGenerator(AnnotationUtils utils, AnnotationMirror min, boolean nullable,
                String typeName, boolean isSupplier) {
            this.utils = utils;
            this.min = min;
            this.nullable = nullable;
            this.typeName = typeName;
            this.isSupplier = isSupplier;
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
            if (nullable) {
                IfBuilder<B> test = bb.ifNotNull(fieldVariableName);
                doGenerate(fieldVariableName, problemsListVariableName, addMethodName, utils, test, parameterName);
                test.endIf();
            } else {
                doGenerate(fieldVariableName, problemsListVariableName, addMethodName, utils, bb, parameterName);
            }
        }

        private <T, B extends BlockBuilderBase<T, B, X>, X> void doGenerate(
                String fieldVariableName, String problemsListVariableName, String addMethodName,
                AnnotationUtils utils, B bb, String parameterName) {

            String stringLit = utils.annotationValue(min, "value", String.class);
            Value v;
            if (isSupplier) {
                v = ClassBuilder.invocationOf("compareTo")
                        .withArgumentFromInvoking("get")
                        .on(fieldVariableName)
                        .onNew(nb -> {
                            nb.withStringLiteral(stringLit)
                                    .ofType(typeName);
                        }).isLessThan(number(0));

            } else {
                v = ClassBuilder.invocationOf("compareTo")
                        .withArgument(fieldVariableName)
                        .onNew(nb -> {
                            nb.withStringLiteral(stringLit)
                                    .ofType(typeName);
                        }).isLessThan(number(0));
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
