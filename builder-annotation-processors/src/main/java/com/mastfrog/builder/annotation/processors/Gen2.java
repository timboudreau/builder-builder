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

import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor;
import static com.mastfrog.builder.annotation.processors.BuilderDescriptors.initDebug;
import com.mastfrog.java.vogon.ClassBuilder;
import java.util.Set;
import javax.lang.model.element.Modifier;

/**
 *
 * @author Tim Boudreau
 */
public class Gen2 {

    private final BuilderDescriptor desc;
    private final Set<BuilderStyles> styles;

    Gen2(BuilderDescriptor desc, Set<BuilderStyles> styles) {
        this.desc = desc;
        this.styles = styles;
    }

    public ClassBuilder<String> generate() {
        ClassBuilder<String> cb = initDebug(ClassBuilder
                .forPackage(desc.packageName())
                .named(desc.targetTypeName + "Builder")
                .docComment("Builder for a " + desc.targetTypeName + ".")
                .withModifier(Modifier.PUBLIC, Modifier.FINAL)
                .autoToString());

        desc.genericsRequiredFor(desc.fields()).forEach(tp -> {
            String qual = desc.generics.nameWithBound(tp);
            cb.withTypeParameters(qual);
        });

//        cb.generateDebugLogCode();
        LocalFieldFactory<String> lff = new LocalFieldFactory<>(desc, cb);
        UnsetCheckerFactory<String> usc = new UnsetCheckerFactory<>(cb, styles, desc, lff::generatorFor);
        lff.generate();

        ValidationMethodFactory<String> vmf = ValidationMethodFactory.create(cb, desc);
        SetterMethodFactory<String> smf = new SetterMethodFactory<>(cb, desc.styles, lff::generatorFor, desc, usc::generatorFor, vmf);
        BuildMethodFactory<String> bmf = new BuildMethodFactory<>(cb, desc, usc, lff::generatorFor);

        smf.generate();

        bmf.flatBuildGenerator().generate();

        return cb.sortMembers();
    }
}
