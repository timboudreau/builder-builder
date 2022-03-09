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
import java.util.Set;
import com.mastfrog.builder.annotations.GenerateBuilder;
import com.mastfrog.builder.annotations.Nullable;
import com.mastfrog.builder.annotations.constraint.DoubleMax;
import com.mastfrog.builder.annotations.constraint.DoubleMin;
import com.mastfrog.builder.annotations.constraint.FloatMax;
import com.mastfrog.builder.annotations.constraint.FloatMin;
import com.mastfrog.builder.annotations.constraint.IntMax;
import com.mastfrog.builder.annotations.constraint.IntMin;
import com.mastfrog.builder.annotations.constraint.LongMax;
import com.mastfrog.builder.annotations.constraint.StringPattern;
import java.io.IOException;
import java.rmi.UnknownHostException;

/**
 *
 * @author Tim Boudreau
 */
public class ComplexThing {

    private final int id;
    private final String name;
    private final long when;
    private final float weight;
    private final Set<String> contents;
    private final OtherThing other;

//    @GenerateBuilder()
    ComplexThing(@IntMin(23) @IntMax(52) int id,
            @Nullable @StringPattern(value = "^[A-Z][a-zA-Z0-9]+$", minLength = 3, maxLength = 24) String name,
            @LongMax(51424280634L) long when,
            @FloatMin(1.5F) @FloatMax(2.5F) float weight,
            Set<String> contents,
            @Nullable OtherThing other,
            boolean isPretty) throws IOException, ClassNotFoundException, UnknownHostException {
        this.id = id;
        this.name = name;
        this.when = when;
        this.weight = weight;
        this.contents = contents;
        this.other = other;
    }

    @Override
    public String toString() {
        return "ComplexThing{" + "id=" + id + ", name=" + name + ", when=" + when + ", weight=" + weight + ", contents=" + contents + ", other=" + other + '}';
    }

//    @GenerateBuilder(styles = {BuilderStyles.FLAT}, codeGenerationVersion = 1)
    public Whoozit whoozit(@IntMax(23) int value, @DoubleMax(1) @DoubleMin(0) double fraction) {
        return new Whoozit(name, value, fraction, this);
    }

    public static class OtherThing {

        public final String name;

        public OtherThing(String name) {
            this.name = name;
        }
    }
}
