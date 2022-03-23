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

package com.mastfrog.builder.annotation.processors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
final class Utils {

    static <T extends Comparable<T>> Set<T> including(T one, Set<? extends T> all) {
        Set<T> result = new HashSet<>(all);
        result.add(one);
        return result;
    }

    static <T extends Comparable<T>> Set<T> combine(Collection<? extends T> a, Collection<? extends T> b) {
        Set<T> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }

    static <T extends Comparable<T>> List<T> sorted(Collection<? extends T> c) {
        List<T> result = new ArrayList<>(c);
        Collections.sort(result);
        return result;
    }

    static <T extends Comparable<T>> Set<T> omitting(T one, Set<T> all) {
        Set<T> result = new HashSet<>(all);
        result.remove(one);
        return result;
    }

    static <T extends Comparable<T>> Set<T> omitting(Set<T> toOmit, Set<T> all) {
        Set<T> result = new HashSet<>(all);
        result.removeAll(toOmit);
        return result;
    }


    private Utils() {
        throw new AssertionError();
    }
}
