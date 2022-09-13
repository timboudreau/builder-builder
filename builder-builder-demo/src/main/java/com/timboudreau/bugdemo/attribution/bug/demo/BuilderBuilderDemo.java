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
package com.timboudreau.bugdemo.attribution.bug.demo;

import com.mastfrog.builder.annotations.GenerateBuilder;
import com.mastfrog.builder.annotations.constraint.CollectionConstraint;
import com.mastfrog.builder.annotations.constraint.IntMax;
import com.mastfrog.builder.annotations.constraint.IntMin;
import com.mastfrog.builder.annotations.constraint.LongMax;
import com.mastfrog.builder.annotations.constraint.LongMin;
import com.mastfrog.builder.annotations.constraint.StringPattern;
import java.io.IOException;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The input - a class with some fields
 *
 * @author Tim Boudreau
 */
public class BuilderBuilderDemo<T, R extends TemporalAccessor, M extends Appendable & CharSequence> {

    final long count;
    final R theR;
    final String name;
    final T theTee;
    final Class<? extends T> tType;
    final Thing thing;
    final List<M> emmm;
    final int[] intArray;

    @GenerateBuilder
    BuilderBuilderDemo(
            Class<? extends T> tType,
            T theTee,
            // Give name a pattern and length constraints
            @StringPattern(maxLength = 24, minLength = 24, value = "^[\\d_]+$") String name,
            R theR,
            // Give count minimum and maximum value constraints
            @LongMax(53) @LongMin(1) long count,
            Thing thing,
            // Ensure the collection size is within  some bounds
            @CollectionConstraint(minSize = 3, maxSize = 16, forbidNullValues = true) List<M> emmm,
            // And set min and max values on an our int[]
            @IntMin(5) @IntMax(123) int[] intArray)
            throws IOException, ClassNotFoundException {
        this.tType = tType;
        this.theTee = theTee;
        this.name = name;
        this.theR = theR;
        this.count = count;
        this.thing = thing;
        this.emmm = emmm;
        this.intArray = intArray;
    }

    public static BuilderBuilderDemoBuilder builder() {
        return new BuilderBuilderDemoBuilder();
    }

    @Override
    public String toString() {
        return "Blork{" + "count=" + count + ", theR=" + theR + ", name="
                + name + ", theTee=" + theTee + ", tType=" + tType
                + ", intArray=" + Arrays.toString(intArray)
                + '}';
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 97 * hash + (int) (this.count ^ (this.count >>> 32));
        hash = 97 * hash + Objects.hashCode(this.theR);
        hash = 97 * hash + Objects.hashCode(this.name);
        hash = 97 * hash + Objects.hashCode(this.theTee);
        hash = 97 * hash + Objects.hashCode(this.tType);
        hash = 97 * hash + Arrays.hashCode(this.intArray);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final BuilderBuilderDemo<?, ?, ?> other = (BuilderBuilderDemo<?, ?, ?>) obj;
        if (this.count != other.count) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.theR, other.theR)) {
            return false;
        }
        if (!Objects.equals(this.theTee, other.theTee)) {
            return false;
        }
        if (!Objects.equals(this.tType, other.tType)) {
            return false;
        }
        return Arrays.equals(this.intArray, other.intArray);
    }
}
