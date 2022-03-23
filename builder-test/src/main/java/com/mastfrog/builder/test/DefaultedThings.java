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

import com.mastfrog.builder.annotations.GenerateBuilder;
import com.mastfrog.builder.annotations.Nullable;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 *
 * @author Tim Boudreau
 */
public class DefaultedThings<Q> {

    private final NavigableMap<String, String> nmap;
    private final Set<Integer> intSet;
    private final long longVal;
    private final int intVal;
    private final byte byteVal;
    private final short shortVal;
    private final float floatVal;
    private final char charVal;
    private final String stringVal;
    private final int[] ints;
    private final StringBuilder sb;

    private final List<String> otherStuff;
    private final Charset charset;
    private final Locale locale;
    private final ZoneId zone;
    private final Optional<StringBuilder> optional;

    private final long longValWithDefault;
    private final int intValWithDefault;
    private final byte byteValWithDefault;
    private final short shortValWithDefault;
    private final float floatValWithDefault;
    private final char charValWithDefault;
    private final String stringValWithDefault;
    private final CharSequence csWithDefault;
    private final boolean defaultedToTrue;
    private final boolean defaultedToFalse;
    private final SortedMap<String, Float> sortedMap;
    private final SortedSet<Optional<String>> sortedSet;
    private final Q[] qs;
    private final HashMap<String, String> hashMap;

    @GenerateBuilder
    public DefaultedThings(
            @Nullable(defaulted = true) HashMap<String, String> hashMap,
            @Nullable(defaulted = true) Q[] qs,
            @Nullable(defaulted = true) SortedMap<String, Float> sortedMap,
            @Nullable(defaulted = true) SortedSet<Optional<String>> sortedSet,
            @Nullable(defaulted = true) int[] ints,
            @Nullable(defaulted = true) StringBuilder sb,
            @Nullable(defaulted = true) NavigableMap<String, String> nmap,
            @Nullable(defaulted = true) Set<Integer> intSet,
            @Nullable(defaulted = true) long longVal,
            @Nullable(defaulted = true) int intVal,
            @Nullable(defaulted = true) byte byteVal,
            @Nullable(defaulted = true) short shortVal,
            @Nullable(defaulted = true) float floatVal,
            @Nullable(defaulted = true) char charVal,
            @Nullable(defaulted = true) String stringVal,
            @Nullable(defaulted = true) List<String> otherStuff,
            @Nullable(defaulted = true) Charset charset,
            @Nullable(defaulted = true) Locale locale,
            @Nullable(defaulted = true) ZoneId zone,
            @Nullable(defaulted = true) Optional<StringBuilder> optional,
            @Nullable(numericDefault = Long.MAX_VALUE) long longValWithDefault,
            @Nullable(numericDefault = 2) int intValWithDefault,
            @Nullable(numericDefault = 3) byte byteValWithDefault,
            @Nullable(numericDefault = 4) short shortValWithDefault,
            @Nullable(numericDefault = 5.1) float floatValWithDefault,
            @Nullable(stringDefault = "z") char charValWithDefault,
            @Nullable(stringDefault = "bleeString") String stringValWithDefault,
            @Nullable(stringDefault = "ploogCs") CharSequence csWithDefault,
            @Nullable(booleanDefault = true) boolean defaultedToTrue,
            @Nullable(booleanDefault = false) boolean defaultedToFalse
    ) {
        this.sortedSet = sortedSet;
        this.ints = ints;
        this.sb = sb;
        this.nmap = nmap;
        this.intSet = intSet;
        this.longVal = longVal;
        this.intVal = intVal;
        this.byteVal = byteVal;
        this.shortVal = shortVal;
        this.floatVal = floatVal;
        this.charVal = charVal;
        this.stringVal = stringVal;
        this.otherStuff = otherStuff;
        this.charset = charset;
        this.locale = locale;
        this.zone = zone;
        this.optional = optional;
        this.longValWithDefault = longValWithDefault;
        this.intValWithDefault = intValWithDefault;
        this.byteValWithDefault = byteValWithDefault;
        this.shortValWithDefault = shortValWithDefault;
        this.floatValWithDefault = floatValWithDefault;
        this.charValWithDefault = charValWithDefault;
        this.stringValWithDefault = stringValWithDefault;
        this.csWithDefault = csWithDefault;
        this.defaultedToTrue = defaultedToTrue;
        this.defaultedToFalse = defaultedToFalse;
        this.hashMap = hashMap;
        this.qs = qs;
        this.sortedMap = sortedMap;
    }

}
