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
public class DoubleMinMaxHandler implements ConstraintHandler {

    private static final String DBL_MIN = "com.mastfrog.builder.annotations.constraint.DoubleMin";
    private static final String DBL_MAX = "com.mastfrog.builder.annotations.constraint.DoubleMax";

    @Override
    public void collect(AnnotationUtils utils, Element targetElement, VariableElement parameterElement,
            Consumer<ConstraintGenerator> genConsumer) {
        AnnotationMirror min = utils.findAnnotationMirror(parameterElement, DBL_MIN);
        AnnotationMirror max = utils.findAnnotationMirror(parameterElement, DBL_MAX);
        if (min != null || max != null) {
            TypeMirror paramType = parameterElement.asType();
            if (!utils.isAssignable(paramType, Double.class.getName())
                    && !utils.isAssignable(paramType, Double.class.getName())) {
                utils.fail("Cannot apply DoubleMin or DoubleMax to a " + paramType,
                        parameterElement, (min == null ? min : max));
                return;
            }
        }
        if (min != null) {
            genConsumer.accept(new DoubleMinGenerator(utils, min));
        }
        if (max != null) {
            genConsumer.accept(new DoubleMaxGenerator(utils, max));
        }
    }

    public boolean equals(Object o) {
        return o instanceof DoubleMinMaxHandler;
    }

    public int hashCode() {
        return DoubleMinMaxHandler.class.hashCode();
    }

    private static class DoubleMaxGenerator implements ConstraintGenerator {

        private final double max;

        DoubleMaxGenerator(AnnotationUtils utils, AnnotationMirror max) {
            this.max = utils.annotationValue(max, "value", Double.class, Double.MAX_VALUE);
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String fieldVariableName, String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            bb.iff().value().expression(fieldVariableName).isGreaterThan(max)
                    .invoke(addMethodName)
                    .withStringConcatentationArgument(fieldVariableName)
                    .append(" must be less than or equal to ")
                    .append(max)
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
            return getClass().getSimpleName() + "(" + max + /* " nullable " + nullable + */ ")";
        }
    }

    private static class DoubleMinGenerator implements ConstraintGenerator {

        private final double min;

        DoubleMinGenerator(AnnotationUtils utils, AnnotationMirror min) {
            this.min = utils.annotationValue(min, "value", Double.class, Double.MIN_VALUE);
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X>
                void generate(String fieldVariableName, String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
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
            return getClass().getSimpleName() + "(" + min + /* " nullable " + nullable + */ ")";
        }
    }
}
