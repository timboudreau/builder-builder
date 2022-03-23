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
import com.mastfrog.builder.annotations.constraint.ByteMax;
import com.mastfrog.builder.annotations.constraint.ByteMin;
import com.mastfrog.builder.annotations.constraint.DoubleMax;
import com.mastfrog.builder.annotations.constraint.DoubleMin;
import com.mastfrog.builder.annotations.constraint.FloatMax;
import com.mastfrog.builder.annotations.constraint.FloatMin;
import com.mastfrog.builder.annotations.constraint.ShortMax;
import com.mastfrog.builder.annotations.constraint.ShortMin;

/**
 *
 * @author Tim Boudreau
 */
public class ManyFields {

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
    private final double f30;
    private final float f31;
    private final byte f32;
    private final short f33;
    private final int f34;
    private final long f35;
    private final char f36;
    private final boolean f37;
    private final double f38;
    private final float f39;
    private final byte f40;
    private final short f41;
    private final int f42;
    private final long f43;
    private final char f44;
    private final boolean f45;
    private final double f46;
    private final float f47;
    private final byte f48;
    private final short f49;
    private final int f50;
    private final long f51;
    private final char f52;
    private final boolean f53;
    private final double f54;
    private final float f55;
    private final byte f56;
    private final short f57;
    private final int f58;
    private final long f59;
    private final char f60;
    private final boolean f61;

    @GenerateBuilder(styles = BuilderStyles.FLAT)
    public ManyFields(byte f0, @ShortMin(5) @ShortMax(15) short f1, int f2, long f3,
            char f4,
            boolean f5,
            @DoubleMin(0.1D) @DoubleMax(1.327856010001D) double f6,
            @FloatMin(1.1111113F) @FloatMax(343.0000000023F) float f7,
            @ByteMin(5) @ByteMax(100) byte f8,
            short f9, int f10, long f11, char f12,
            boolean f13, double f14, float f15, byte f16, short f17, int f18,
            long f19, char f20, boolean f21, double f22, float f23, byte f24,
            short f25, int f26, long f27, char f28, boolean f29, double f30,
            float f31, byte f32, short f33, int f34, long f35, char f36,
            boolean f37, double f38, float f39, byte f40, short f41, int f42,
            long f43, char f44, boolean f45, double f46, float f47, byte f48,
            short f49, int f50, long f51, char f52, boolean f53, double f54,
            float f55, byte f56, short f57, int f58, long f59, char f60,
            boolean f61) {
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
        this.f30 = f30;
        this.f31 = f31;
        this.f32 = f32;
        this.f33 = f33;
        this.f34 = f34;
        this.f35 = f35;
        this.f36 = f36;
        this.f37 = f37;
        this.f38 = f38;
        this.f39 = f39;
        this.f40 = f40;
        this.f41 = f41;
        this.f42 = f42;
        this.f43 = f43;
        this.f44 = f44;
        this.f45 = f45;
        this.f46 = f46;
        this.f47 = f47;
        this.f48 = f48;
        this.f49 = f49;
        this.f50 = f50;
        this.f51 = f51;
        this.f52 = f52;
        this.f53 = f53;
        this.f54 = f54;
        this.f55 = f55;
        this.f56 = f56;
        this.f57 = f57;
        this.f58 = f58;
        this.f59 = f59;
        this.f60 = f60;
        this.f61 = f61;
    }

    @Override
    public String toString() {
        return "ManyFields{" + "f0=" + f0 + ", f1=" + f1 + ", f2=" + f2
                + ", f3=" + f3 + ", f4=" + f4 + ", f5=" + f5 + ", f6=" + f6
                + ", f7=" + f7 + ", f8=" + f8 + ", f9=" + f9 + ", f10="
                + f10 + ", f11=" + f11 + ", f12=" + f12 + ", f13=" + f13
                + ", f14=" + f14 + ", f15=" + f15 + ", f16=" + f16 + ", f17="
                + f17 + ", f18=" + f18 + ", f19=" + f19 + ", f20=" + f20 + ", f21="
                + f21 + ", f22=" + f22 + ", f23=" + f23 + ", f24=" + f24 + ", f25="
                + f25 + ", f26=" + f26 + ", f27=" + f27 + ", f28=" + f28 + ", f29="
                + f29 + ", f30=" + f30 + ", f31=" + f31 + ", f32=" + f32 + ", f33="
                + f33 + ", f34=" + f34 + ", f35=" + f35 + ", f36=" + f36 + ", f37="
                + f37 + ", f38=" + f38 + ", f39=" + f39 + ", f40=" + f40 + ", f41="
                + f41 + ", f42=" + f42 + ", f43=" + f43 + ", f44=" + f44 + ", f45="
                + f45 + ", f46=" + f46 + ", f47=" + f47 + ", f48=" + f48 + ", f49="
                + f49 + ", f50=" + f50 + ", f51=" + f51 + ", f52=" + f52 + ", f53="
                + f53 + ", f54=" + f54 + ", f55=" + f55 + ", f56=" + f56 + ", f57="
                + f57 + ", f58=" + f58 + ", f59=" + f59 + ", f60=" + f60 + ", f61="
                + f61 + '}';
    }

    public static void main(String[] args) {
        for (int i = 0; i < 9; i++) {
            String t;
            switch (i % 8) {
                case 0:
                    t = "byte";
                    break;
                case 1:
                    t = "short";
                    break;
                case 2:
                    t = "int";
                    break;
                case 3:
                    t = "long";
                    break;
                case 4:
                    t = "char";
                    break;
                case 5:
                    t = "boolean";
                    break;
                case 6:
                    t = "double";
                    break;
                case 7:
                    t = "float";
                    break;
                default:
                    t = "int";
            }
            System.out.println("private final " + t + " f" + i + ";");
        }
    }
}
