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

/**
 * Flavors of generic signature that can be generated.
 *
 * @author Tim Boudreau
 */
public enum GenericSignatureKind {

    /**
     * Generics, if present, are reduced to &lt;&gt; - used for generating
     * return type strings.
     */
    INFERRED_BOUNDS,
    /**
     * Generics with signatures intact and all bounds, e.g.
     * &lt;Foo extends Bar, Baz extends Hoo &amp; Hah&gt;.
     */
    EXPLICIT_BOUNDS,
    /**
     * Generics with bounds elided, e.g. &lt;Foo, Baz&gt;
     */
    IMPLICIT_BOUNDS,
}
