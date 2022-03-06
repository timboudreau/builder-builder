/*
 * The MIT License
 *
 * Copyright 2020 Mastfrog Technologies.
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
package com.mastfrog.builder.api;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 *
 * @author Tim Boudreau
 */
public abstract class BuilderBase<O, T, B extends BuilderBase<O, T, B>> {

    protected final BiFunction<B, T, O> converter;
    private boolean built;

    protected BuilderBase(BiFunction<B, T, O> converter) {
        this.converter = converter;
    }

    protected final boolean built() {
        return built;
    }

    protected <T1, B1 extends BuilderBase<B, T1, B1>>
            B sub(Function<Void, B1> f, Consumer<B1> c) {

        return cast();
    }

    protected O build(T obj) {
        O result = converter.apply(cast(), obj);
        built = true;
        return result;
    }

    @SuppressWarnings("uncheckeds")
    protected final B cast() {
        return (B) this;
    }

    private static final class Hold<T> {

        private T value;
        private final String msg;

        public Hold(String msg) {
            this.msg = msg;
        }

        T get() {
            if (value == null) {
                throw new IllegalStateException(msg);
            }
            return value;
        }

        void set(T value) {
            this.value = value;
        }
    }
}
