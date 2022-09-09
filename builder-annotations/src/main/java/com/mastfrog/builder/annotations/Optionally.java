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

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a parameter used in generating a builder as optional. The generated
 * builder may still not accept being <i>passed</i> null to a setter on it, but
 * it will not prevent building the builder without ever calling a setter for
 * it.
 * <p>
 * Optional parameters can have a default value - either the natural "null
 * value" for types that have one (numbers, collections, strings, arrays), or
 * for simple types (strings, numbers, characters, booleans), an explicit
 * default may be provided, depending on how you configure this annotation.
 * </p>
 * <p>
 * An instance of this annotation applies to exactly <i>one</i> method or
 * constructor parameter, and no more than <i>one</i> of the methods on this
 * annotation may have a value.
 * </p>
 *
 * @author Tim Boudreau
 */
@Retention(RUNTIME)
@Target(PARAMETER)
public @interface Optionally {

    /**
     * For types that have a reasonable "null value" - zero for numbers, empty
     * string/set/map/list/collection/array/optional, plus a few things which
     * have obvious or conventional defaults, synthesize that value rather than
     * passing an actual null.
     * <p>
     * <b>Note:</b> If there is a constraint on the size, minimum or maximum
     * value, and the null value does not fit within the constraint, it is
     * possible to create builders which always fail when an optional value is
     * not present, with an error message that will not make any sense to users
     * of it about a value they did not set.
     * </p>
     * <ul>
     * <li><code>Any Number or Character type</code> -- zero</li>
     * <li><i>Any array type</i> -- An empty array of that type (including type
     * variables)</li>
     * <li><code>String</code> -- Empty string</li>
     * <li><code>CharSequence</code> -- Empty string</li>
     * <li><code>StringBuilder</code> -- New instance</li>
     * <li><code>Optional</code> -- Optional.empty()</li>
     * <li><code>Collection</code> -- Empty instance</li>
     * <li><code>List</code> -- Empty instance</li>
     * <li><code>ArrayList</code> -- Instance with capacity 1</li>
     * <li><code>LinkedList</code> -- Empty instance</li>
     * <li><code>Map</code> -- Empty instance</li>
     * <li><code>NavigableMap</code> -- Empty instance</li>
     * <li><code>SortedMap</code> -- Empty instance</li>
     * <li><code>HashMap</code> -- New instance with capacity 1</li>
     * <li><code>TreeMap</code> -- New instance</li>
     * <li><code>Set</code> -- Empty instance</li>
     * <li><code>SortedSet</code> -- Empty instance</li>
     * <li><code>NavigableSet</code> -- Empty instance</li>
     * <li><code>HashSet</code> -- New instance</li>
     * <li><code>TreeSet</code> -- New instance</li>
     * <li><code>Iterable</code> -- Empty instance</li>
     * <li><code>Iterator</code> -- Empty instance</li>
     * <li><code>Enumeration</code> -- Empty instance</li>
     * <li><code>Duration</code> -- Duration.ZERO</li>
     * <li><code>Charset</code> -- StandardCharsets.UTF_8</li>
     * <li><code>Locale</code> -- Locale.getDefault()</li>
     * <li><code>ZoneId</code> -- ZoneId.of("GMT")</li>
     * </ul>
     *
     * @return Whether to pass null (where possible) or the logical null-value
     */
    boolean defaulted() default false;

    /**
     * String defaults can be provided for <code>String</code>,
     * <code>CharSequence</code> and (length 1) <code>char</code> or
     * <code>Character</code> parameters.
     *
     * @return A string
     */
    String stringDefault() default "";

    /**
     * Number defaults.
     *
     * @return a double
     */
    double numericDefault() default 0;

    /**
     * Boolean defaults.
     *
     * @return a boolean
     */
    boolean booleanDefault() default false;

    /**
     * If true, then builders should not throw an exception if null is passed to
     * a setter method.
     *
     * @return false by default
     */
    boolean acceptNull() default false;
}
