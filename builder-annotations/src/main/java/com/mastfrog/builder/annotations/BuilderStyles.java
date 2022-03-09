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

/**
 * Style flags that impact builder generation.
 *
 * @author Tim Boudreau
 */
public enum BuilderStyles {
    /**
     * If present, if a builder would include a property for some other type for
     * which we are creating a builder, include a builder for that from a
     * method which takes a <code>Consumer&lt;ThatBuilder&gt;</code>.
     */
    CLOSURES,
    /**
     * Generate a single simple builder class that throws an exception if a
     * required parameter has not been set; the default generation mode creates
     * sub-builder types for the cartesian product of the required fields, such
     * that omitting a required property is a compile-time error (since you do
     * not have a builder with a build method until you are on the last
     * parameter, which you pass to it - e.g. if you have the parameters foo,
     * bar and baz you could call
     * <pre>Thing thing = new ThingBuilder().withFoo(foo).withBar(bar).buildWithBaz(baz);</pre>
     * or
     * <pre>Thing thing = new ThingBuilder().withBaz(baz).withBar(bar).buildWithFoo(foo);</pre>
     * and both are legal (the first call returns a
     * <code>ThingBuilderWithFoo</code> in the first case and a
     * <code>ThingBuilderWithBaz</code> in the second case.
     * <p>
     * The cartesian product generation style is more powerful, but it does
     * result in a large number of classes being generated, so if you have a
     * very large number of parameters, the <code>FLAT</code> style may be
     * preferred.
     * </p>
     */
    FLAT,
    /**
     * If included, make the builder class package private rather than exposing
     * it.
     */
    PACKAGE_PRIVATE,
    ;
}
