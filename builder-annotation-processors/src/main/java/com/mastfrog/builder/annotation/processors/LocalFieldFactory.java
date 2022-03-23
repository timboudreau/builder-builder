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
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor.FieldDescriptor;
import com.mastfrog.java.vogon.ClassBuilder;
import java.util.HashMap;
import java.util.Map;
import javax.lang.model.element.Modifier;

/**
 *
 * @author Tim Boudreau
 */
public class LocalFieldFactory<C> {

    private final BuilderDescriptor desc;
    private final ClassBuilder<C> bldr;
    private final Map<FieldDescriptor, LocalFieldGenerator> fields = new HashMap<>();

    LocalFieldFactory(BuilderDescriptor desc, ClassBuilder<C> bldr) {
        this.desc = desc;
        this.bldr = bldr;
    }

    static <C> LocalFieldFactory<C> create(BuilderDescriptor desc, ClassBuilder<C> bldr) {
        return new LocalFieldFactory<>(desc, bldr);
    }

    public LocalFieldGenerator generatorFor(FieldDescriptor fd) {
        return fields.computeIfAbsent(fd, f -> new DefaultFieldGenerator(fd));
    }

    public void generate() {
        for (FieldDescriptor fd : desc.fields()) {
            generatorFor(fd).generate(false);
        }
    }

    public interface LocalFieldGenerator {

        void generate(boolean makeFinal);

        String localFieldName();
    }

    class DefaultFieldGenerator implements LocalFieldGenerator {

        private final FieldDescriptor desc;
        String name;
        private boolean generated;

        public DefaultFieldGenerator(FieldDescriptor desc) {
            this.desc = desc;
        }

        @Override
        public void generate(boolean makeFinal) {
            if (!generated) {
                generated = true;
                ClassBuilder.FieldBuilder<ClassBuilder<C>> field = bldr.field(localFieldName())
                        .withModifier(Modifier.PRIVATE);
                if (makeFinal) {
                    field.withModifier(Modifier.FINAL);
                }
                field.ofType(desc.typeName());
            }
        }

        @Override
        public String localFieldName() {
            if (name == null) {
                name = bldr.unusedFieldName("_p_" + desc.fieldName);
            }
            return name;
        }
    }
}
