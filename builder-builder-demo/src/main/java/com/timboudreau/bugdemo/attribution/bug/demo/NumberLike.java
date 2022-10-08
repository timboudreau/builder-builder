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
import com.mastfrog.builder.annotations.Optionally;
import com.mastfrog.builder.annotations.constraint.BigMax;
import com.mastfrog.builder.annotations.constraint.BigMin;
import com.mastfrog.builder.annotations.constraint.DoubleMax;
import com.mastfrog.builder.annotations.constraint.DoubleMin;
import com.mastfrog.builder.annotations.constraint.LongMax;
import com.mastfrog.builder.annotations.constraint.LongMin;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 *
 * @author Tim Boudreau
 */
public final class NumberLike {

    @GenerateBuilder
    public NumberLike(
            @BigMin("100.02") @BigMax("1000000.325") BigDecimal bigDec,
            @BigMin("200") @BigMax("2327") BigInteger bigInt,
            @LongMin(22) @LongMax(122) NumThingOne numOne,
            @DoubleMin(22.1) @DoubleMax(122.325) NumThingOne numTwo,
            @Optionally @DoubleMin(1) @DoubleMax(5) Double nullableDouble
    ) {

    }

    public static class NumThingOne extends Number {

        private final int ix;

        public NumThingOne(int ix) {
            this.ix = ix;
        }

        @Override
        public int intValue() {
            return ix;
        }

        @Override
        public long longValue() {
            return ix;
        }

        @Override
        public float floatValue() {
            return (float) ix;
        }

        @Override
        public double doubleValue() {
            return (double) ix;
        }

    }
}
