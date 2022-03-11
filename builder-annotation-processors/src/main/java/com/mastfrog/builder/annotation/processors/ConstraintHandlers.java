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
package com.mastfrog.builder.annotation.processors;

import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.builder.annotation.processors.builtinconstraints.ByteMinMaxHandler;
import com.mastfrog.builder.annotation.processors.builtinconstraints.CollectionAndArraySizeHandler;
import com.mastfrog.builder.annotation.processors.builtinconstraints.DoubleMinMaxHandler;
import com.mastfrog.builder.annotation.processors.builtinconstraints.FloatMinMaxHandler;
import com.mastfrog.builder.annotation.processors.builtinconstraints.IntMinMaxHandler;
import com.mastfrog.builder.annotation.processors.builtinconstraints.LongMinMaxHandler;
import com.mastfrog.builder.annotation.processors.builtinconstraints.ShortMinMaxHandler;
import com.mastfrog.builder.annotation.processors.builtinconstraints.StringPatternHandler;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.builder.annotation.processors.spi.ConstraintHandler;
import java.util.Collections;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import javax.lang.model.element.Element;
import javax.lang.model.element.VariableElement;

/**
 *
 * @author Tim Boudreau
 */
final class ConstraintHandlers implements ConstraintHandler {

    private static Set<ConstraintHandler> all;

    private static synchronized Set<ConstraintHandler> all() {
        if (all == null) {
            Set<ConstraintHandler> result = new HashSet<>();
            for (ConstraintHandler h : ServiceLoader.load(ConstraintHandler.class, Thread.currentThread().getContextClassLoader())) {
                result.add(h);
            }
            all = result;
            all.add(new IntMinMaxHandler());
            all.add(new LongMinMaxHandler());
            all.add(new ShortMinMaxHandler());
            all.add(new ByteMinMaxHandler());
            all.add(new FloatMinMaxHandler());
            all.add(new DoubleMinMaxHandler());
            all.add(new StringPatternHandler());
            all.add(new CollectionAndArraySizeHandler());
        }
        return all;
    }
    private final AnnotationUtils utils;

    public ConstraintHandlers(AnnotationUtils utils) {
        this.utils = utils;
    }

    Set<ConstraintGenerator> generators(Element targetElement,
            VariableElement parameterElement) {
        Set<ConstraintGenerator> result = new HashSet<>();
        collect(utils, targetElement, parameterElement, result::add);
        return result.isEmpty() ? Collections.emptySet() : result;
    }

    @Override
    public void collect(AnnotationUtils utils, Element targetElement,
            VariableElement parameterElement, Consumer<ConstraintGenerator> genConsumer) {
        for (ConstraintHandler h : all()) {
            h.collect(utils, targetElement, parameterElement, genConsumer);
        }
    }
}
