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
package com.mastfrog.builder.annotations;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Indicate that a builder should be generated for a given method or class.
 *
 * @author Tim Boudreau
 */
@Retention(RUNTIME)
@Target({CONSTRUCTOR, METHOD})
public @interface GenerateBuilder {

    /**
     * The name of the generated builder; if unset, it will be the class name of
     * the target type with <i>Builder</i> appended to it.
     *
     * @return A class name
     */
    String className() default "";

    /**
     * The set of style flags that affect code generation.
     *
     * @return A set of styles
     */
    BuilderStyles[] styles() default {BuilderStyles.CLOSURES};

    /**
     * If this library evolves in ways that generate code that would be
     * incompatible with a previous version, specify the version of this library
     * code generation should adhere to.
     *
     * @return an integer
     */
    int codeGenerationVersion() default 1;
}
