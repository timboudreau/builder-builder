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
package com.mastfrog.builder.annotation.processors.spi;

import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.builder.annotation.processors.BuilderAnnotationProcessor;
import com.mastfrog.java.vogon.ClassBuilder;
import java.util.function.Consumer;

/**
 * A contraint on a builder parameter, which can generate validation code into
 * either setter or build methods.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ConstraintGenerator extends Comparable<ConstraintGenerator> {

    public static final String NULLABLE_ANNOTATION = BuilderAnnotationProcessor.NULLABLE;

    /**
     * Generate a test into a method represented by the passed block builder. If
     * the test fails, call <code>addMethodName</code> on the object
     * <code>problemsListVariableName</code> with a single string argument
     * containing a human readable description of the problem.
     *
     * @param <T> Generic types required by the builder
     * @param <B> Generic types required by the builder
     * @param <X> Generic types required by the builder
     * @param localFieldName The field name of the variable as it exists in the
     * builder. In the case of name collisions, this may or may not be the same
     * name as the original parameter.
     * @param problemsListVariableName The name of an object with a method to
     * pass a problem to
     * @param addMethodName The name of the method to call with any problems
     * @param utils Annotation utils, for querying types
     * @param into The block to build the test (typically opening with an if
     * statement) into
     * @param parameterName The name of the parameter name as it originally
     * appeard, for use in problem messages
     */
    <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(String localFieldName,
            String problemsListVariableName, String addMethodName, AnnotationUtils utils, B into,
            String parameterName);

    /**
     * If this generator can benefit from adding some fields to the class (such
     * as a static Pattern instance for a string constraint), do that here.
     *
     * @param <C> The parameter type of the class builder
     * @param bldr A class builder
     */
    default <C> void decorateClass(ClassBuilder<C> bldr) {
    }

    /**
     * Contribute a bullet point to a list of constraint doc comments in the
     * javadoc of a setter on a builder.
     *
     * @param bulletPoints A consumer for strings, typically one per constraint
     */
    default void contributeDocComments(Consumer<String> bulletPoints) {

    }

    /**
     * If a constraint performs a more expensive test, it should have a greater
     * weight, so that less expensive tests have a chance to fail first.
     *
     * @return zero by default
     */
    default int weight() {
        return 0;
    }

    /**
     * Compare implementation, using <code>weight()</code> as the basis.
     *
     * @param o Another constraint
     * @return An integer
     */
    @Override
    default int compareTo(ConstraintGenerator o) {
        return Integer.compare(weight(), o.weight());
    }
}
