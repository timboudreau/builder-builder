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
package com.mastfrog.builder.annotations.constraint;

import static java.lang.annotation.ElementType.PARAMETER;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Map;

/**
 * Constraints that can be enforced against collections and arrays.
 *
 * @author Tim Boudreau
 */
@Retention(RUNTIME)
@Target(PARAMETER)
@AppliesTo({
    Collection.class,
    Map.class,
    Object.class,
    int[].class,
    byte[].class,
    short[].class,
    long[].class,
    char[].class,
    float[].class,
    double[].class,
    boolean[].class,})
public @interface CollectionConstraint {

    /**
     * The minimum collection size, inclusive.
     *
     * @return an int &lt;= maxSize()
     */
    int minSize() default 0;

    /**
     * The maximum collection size, <b>inclusive</b>.
     *
     * @return
     */
    int maxSize() default Integer.MAX_VALUE;

    /**
     * Check for nulls when applying or building and throw if any are present;
     * not applicable to primitive arrays.
     *
     * @return a boolean
     */
    boolean forbidNullValues() default false;

    /**
     * Check the type of all elements to be assignable to this type, if
     * specified as something other than java.lang.Object. This allows for
     * applying the &064;SafeVarags annotation since builder setters for arrays
     * use varargs, and allows for the creation of generic arrays without a
     * <i>Generic array creation</i> error.
     * <p>
     * Non-applicable to primitive arrays.
     * </p>
     *
     * @return A type to check against
     */
    Class<?> checked() default Object.class;
}
