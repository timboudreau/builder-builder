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
package com.mastfrog.builder.test;

import com.mastfrog.builder.annotations.GenerateBuilder;
import com.mastfrog.builder.annotations.constraint.CollectionConstraint;
import com.mastfrog.builder.annotations.constraint.IntMax;
import com.mastfrog.builder.annotations.constraint.IntMin;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import com.mastfrog.builder.annotations.Optionally;

/**
 *
 * @author Tim Boudreau
 */
public class Gwerb<T> {

    private final Set<String> strings;
    private final int[] ints;
    private final Object[] objs;
    private final Map<String, Object> stuff;

    @GenerateBuilder
    public Gwerb(
            @CollectionConstraint(minSize = 1, maxSize = 10, forbidNullValues = true) Set<String> strings,
            @IntMax(52) @IntMin(12) @Optionally @CollectionConstraint(minSize = 2, maxSize = 12) int[] ints,
            @CollectionConstraint(minSize = 3, maxSize = 34, forbidNullValues = true,
                    checked = CharSequence.class) @Optionally(acceptNull = true, defaulted = true) T[] objs,
            @CollectionConstraint(minSize = 7, maxSize = 17) Map<String, Object> stuff) {
        this.strings = strings;
        this.ints = ints;
        this.objs = objs;
        this.stuff = stuff;
    }

    @Override
    public String toString() {
        return "Gwerb{" + "strings=" + strings + ", ints="
                + Arrays.toString(ints) + ", objs=" + Arrays.toString(objs)
                + ", stuff=" + stuff + '}';
    }

}
