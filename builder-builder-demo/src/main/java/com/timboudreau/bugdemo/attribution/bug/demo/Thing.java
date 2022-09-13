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

import static com.mastfrog.builder.annotations.BuilderStyles.FLAT;
import com.mastfrog.builder.annotations.GenerateBuilder;
import com.mastfrog.builder.annotations.Optionally;
import com.mastfrog.builder.annotations.constraint.ShortMax;
import com.mastfrog.builder.annotations.constraint.ShortMin;
import com.mastfrog.builder.annotations.constraint.StringPattern;
import java.util.Objects;

/**
 * A thing.
 *
 * @author Tim Boudreau
 */
public class Thing {

    private final String stringValue;
    private final short shortValue;

    @GenerateBuilder(styles = FLAT)
    public Thing(
            @Optionally(acceptNull = true, stringDefault = "thing")
            @StringPattern(value = "^[a-z]+$", minLength = 1, maxLength = 20) String stringValue,
            @ShortMin(23) @ShortMax(42) short shortValue) {
        this.stringValue = stringValue;
        this.shortValue = shortValue;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.stringValue);
        hash = 53 * hash + this.shortValue;
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
        final Thing other = (Thing) obj;
        if (this.shortValue != other.shortValue) {
            return false;
        }
        return Objects.equals(this.stringValue, other.stringValue);
    }

    @Override
    public String toString() {
        return "Thing{" + "stringValue=" + stringValue + ", shortValue=" + shortValue + '}';
    }

}
