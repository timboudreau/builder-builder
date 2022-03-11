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

import com.mastfrog.builder.annotations.BuilderStyles;
import com.mastfrog.builder.annotations.GenerateBuilder;
import com.mastfrog.builder.annotations.Nullable;
import com.mastfrog.builder.annotations.constraint.CollectionConstraint;
import com.mastfrog.builder.annotations.constraint.LongMax;
import com.mastfrog.builder.annotations.constraint.LongMin;
import com.mastfrog.builder.annotations.constraint.StringPattern;
import java.io.IOException;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
public class Blork<T, R extends TemporalAccessor, M extends Appendable & CharSequence> {

    private final long count;
    private final R theR;
    private final String name;
    private final T theTee;
    private final Class<? extends T> tType;

    @GenerateBuilder(styles = BuilderStyles.FLAT)
    Blork(Class<? extends T> tType, T theTee,
            @StringPattern(maxLength = 24, minLength = 24, value = "^[\\d_]+$") String name, 
            R theR,
            @LongMax(53) @LongMin(1) long count,
            ComplexThing complex,
            @CollectionConstraint(minSize=3, maxSize=16, forbidNullValues = true) List<M> emmm)
            throws IOException, ClassNotFoundException {
        this.tType = tType;
        this.theTee = theTee;
        this.name = name;
        this.theR = theR;
        this.count = count;
    }

    @Override
    public String toString() {
        return "Blork{" + "count=" + count + ", theR=" + theR + ", name="
                + name + ", theTee=" + theTee + ", tType=" + tType + '}';
    }

}
