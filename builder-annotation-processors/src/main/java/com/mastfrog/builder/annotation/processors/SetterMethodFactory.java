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

import static com.mastfrog.annotation.AnnotationUtils.capitalize;
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor;
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor.FieldDescriptor;
import com.mastfrog.builder.annotation.processors.UnsetCheckerFactory.UnsetCheckGenerator;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import static com.mastfrog.java.vogon.ClassBuilder.variable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.lang.model.element.Modifier;

/**
 *
 * @author Tim Boudreau
 */
public class SetterMethodFactory<C> {

    private final ClassBuilder<C> bldr;
    private final Set<BuilderStyles> styles;
    private final Function<? super BuilderDescriptors.BuilderDescriptor.FieldDescriptor, ? extends LocalFieldFactory.LocalFieldGenerator> fields;
    private final BuilderDescriptor desc;
    private final Map<FieldDescriptor, SetterMethodGenerator> generatorForField = new HashMap<>();
    private String failMethod;
    private final Function<FieldDescriptor, UnsetCheckGenerator> checkers;

    public SetterMethodFactory(ClassBuilder<C> bldr, Set<BuilderStyles> styles,
            Function<? super BuilderDescriptor.FieldDescriptor, ? extends LocalFieldFactory.LocalFieldGenerator> fields,
            BuilderDescriptor desc,
            Function<FieldDescriptor, UnsetCheckGenerator> checkers) {
        this.bldr = bldr;
        this.styles = styles;
        this.fields = fields;
        this.desc = desc;
        this.checkers = checkers;
    }

    static <C> SetterMethodFactory<C> create(ClassBuilder<C> bldr, BuilderDescriptor desc,
            LocalFieldFactory lff, UnsetCheckerFactory unset) {
        return new SetterMethodFactory<>(bldr, desc.styles, lff::generatorFor, desc,
                unset::generatorFor);
    }

    public SetterMethodGenerator generatorFor(FieldDescriptor fd) {
        return generatorForField.computeIfAbsent(fd, SetterMethodGeneratorImpl::new);
    }

    private String failMethod() {
        if (failMethod != null) {
            return failMethod;
        }
        ClassBuilder<?> top = bldr.topLevel();
        if (top.containsMethodNamed("__fail__")) {
            return "__fail__";
        }
        failMethod = "__fail__";
        top.method(failMethod, mb -> {
            mb.addArgument("String", "invalidArgument");
            mb.withModifier(Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC);
            mb.body(bb -> {
                bb.andThrow(nb -> {
                    nb.withArgument("invalidArgument")
                            .ofType("IllegalArgumentException");
                });
            });
        });
        return failMethod;
    }

    public void generate() {
        for (FieldDescriptor fd : desc.fields()) {
            generatorFor(fd).generate();
        }
    }

    public interface SetterMethodGenerator {

        void generate();
    }

    class SetterMethodGeneratorImpl implements SetterMethodGenerator {

        private final FieldDescriptor field;

        public SetterMethodGeneratorImpl(FieldDescriptor field) {
            this.field = field;
        }

        @Override
        public void generate() {
            boolean nullable = !field.isPrimitive();

            bldr.method("with" + capitalize(field.fieldName), mb -> {
                if (field.canBeVarargs()) {
                    // Compiler requires final if we use @SafeVarargs
                    mb.withModifier(Modifier.FINAL);
                    mb.annotatedWith("SafeVarargs").closeAnnotation();
                    mb.addArgument(field.parameterTypeName(), field.fieldName);
                } else {
                    mb.addArgument(field.typeName(), field.fieldName);
                }
                mb.withModifier(Modifier.PUBLIC)
                        .docComment(field.setterJavadoc())
                        .returning(bldr.parameterizedClassName(false))
                        .body(bb -> {
                            if (nullable && !field.optional) {
                                bb.ifNull(field.fieldName)
                                        .andThrow(nb -> {
                                            nb.withStringConcatentationArgument("Parameter '")
                                                    .append(field.fieldName)
                                                    .append("' may not be null.")
                                                    .endConcatenation()
                                                    .ofType("IllegalArgumentException");
                                        })
                                        .endIf();
                            }
                            String builderField = fields.apply(field).localFieldName();
                            for (ConstraintGenerator c : field.constraintsSorted()) {
                                c.decorateClass(bldr.topLevel());
                                c.generate(field.fieldName, bldr.topLevel().className(), failMethod(), desc.utils(), bb, field.fieldName);
                            }
                            checkers.apply(field).onSet(bb);
                            bb.assign("this." + builderField)
                                    .toExpression(field.fieldName);
                            bb.returningThis();
                        });
            });
        }
    }
}
