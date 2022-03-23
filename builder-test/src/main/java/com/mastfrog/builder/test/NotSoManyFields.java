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

import com.mastfrog.builder.annotations.*;

/**
 *
 * @author Tim Boudreau
 */
public class NotSoManyFields {

    private final byte f0;
    private final short f1;
    private final int f2;
    private final long f3;
    private final char f4;
    private final boolean f5;
    private final double f6;
    private final float f7;
    private final byte f8;
    private final short f9;
    private final int f10;
    private final long f11;
    private final char f12;
    private final boolean f13;
    private final double f14;
    private final float f15;
    private final byte f16;
    private final short f17;
    private final int f18;
    private final long f19;
    private final char f20;
    private final boolean f21;
    private final double f22;
    private final float f23;
    private final byte f24;
    private final short f25;
    private final int f26;
    private final long f27;
    private final char f28;
    private final boolean f29;

    @GenerateBuilder(styles = BuilderStyles.FLAT)
    public NotSoManyFields(byte f0, short f1, int f2, long f3, char f4, boolean f5, double f6, float f7, byte f8, short f9, int f10, long f11, char f12, boolean f13, double f14, float f15, byte f16, short f17, int f18, long f19, char f20, boolean f21, double f22, float f23, byte f24, short f25, int f26, long f27, char f28, boolean f29) {
        this.f0 = f0;
        this.f1 = f1;
        this.f2 = f2;
        this.f3 = f3;
        this.f4 = f4;
        this.f5 = f5;
        this.f6 = f6;
        this.f7 = f7;
        this.f8 = f8;
        this.f9 = f9;
        this.f10 = f10;
        this.f11 = f11;
        this.f12 = f12;
        this.f13 = f13;
        this.f14 = f14;
        this.f15 = f15;
        this.f16 = f16;
        this.f17 = f17;
        this.f18 = f18;
        this.f19 = f19;
        this.f20 = f20;
        this.f21 = f21;
        this.f22 = f22;
        this.f23 = f23;
        this.f24 = f24;
        this.f25 = f25;
        this.f26 = f26;
        this.f27 = f27;
        this.f28 = f28;
        this.f29 = f29;
    }

    @Override
    public String toString() {
        return "NotSoManyFields{" + "f0=" + f0 + ", f1=" + f1 + ", f2=" + f2
                + ", f3=" + f3 + ", f4=" + f4 + ", f5=" + f5 + ", f6=" + f6
                + ", f7=" + f7 + ", f8=" + f8 + ", f9=" + f9 + ", f10=" + f10
                + ", f11=" + f11 + ", f12=" + f12 + ", f13=" + f13 + ", f14="
                + f14 + ", f15=" + f15 + ", f16=" + f16 + ", f17=" + f17 + ", f18="
                + f18 + ", f19=" + f19 + ", f20=" + f20 + ", f21=" + f21 + ", f22="
                + f22 + ", f23=" + f23 + ", f24=" + f24 + ", f25=" + f25 + ", f26="
                + f26 + ", f27=" + f27 + ", f28=" + f28 + ", f29=" + f29 + '}';
    }
}
