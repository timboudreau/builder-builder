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
import java.util.EnumSet;
import java.util.Set;
import javax.lang.model.element.AnnotationMirror;

/**
 * Mirrors the styles enum in the enums package.
 *
 * @author Tim Boudreau
 */
enum BuilderStyles {
    /**
     * The (badly named) default.
     */
    CLOSURES,
    /**
     * If set, generate "flat" builders which have a build method that throws,
     * rather than using nested types that only return a builder that <i>has</i>
     * a build method when all required parameters have been set (requires more
     * builder classes, but turns incompletely initialiazed objects from a
     * runtime error to a compile-type error, which is always a good thing).
     */
    FLAT,
    /**
     * If set, generated builders should be package private.
     */
    PACKAGE_PRIVATE,
    /**
     * If set, debug comments showing the source line that generated some code
     * will be generated.
     */
    DEBUG;

    static Set<BuilderStyles> styles(AnnotationUtils utils, AnnotationMirror in) {
        // Don't fail the way valueOf() does
        Set<String> all = utils.enumConstantValues(in, "styles", CLOSURES.name());
        Set<BuilderStyles> result = EnumSet.noneOf(BuilderStyles.class);
        BuilderStyles[] possible = BuilderStyles.values();
        if (all != null) {
            for (String s : all) {
                for (BuilderStyles bs : possible) {
                    if (bs.matches(s)) {
                        result.add(bs);
                    }
                }
            }
        }
        return result;
    }

    boolean matches(String what) {
        if (what == null) {
            return false;
        }
        if (name().equals(what)) {
            return true;
        } else if (name().toLowerCase().equals(what.toLowerCase())) {
            return true;
        } else if (toString().toLowerCase().equals(what.toLowerCase())) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return name().toLowerCase().replace('_', '-');
    }
}
