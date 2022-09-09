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
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor.FieldDescriptor;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ValueExpressionBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;

/**
 *
 * @author Tim Boudreau
 */
public class ValidationMethodFactory<C> {

    private final ClassBuilder<C> bldr;
    private final BuilderDescriptors.BuilderDescriptor desc;
    private final Map<FieldDescriptor, ValidationMethodGenerator> generators
            = new HashMap<>();
    private String failMethod;
    private String nullCheckMethod;

    ValidationMethodFactory(ClassBuilder<C> bldr, BuilderDescriptors.BuilderDescriptor desc) {
        this.bldr = bldr;
        this.desc = desc;
    }

    static <C> ValidationMethodFactory<C> create(ClassBuilder<C> bldr, BuilderDescriptors.BuilderDescriptor desc) {
        return new ValidationMethodFactory<>(bldr, desc);
    }

    String nullCheckMethod() {
        if (nullCheckMethod == null) {
            nullCheckMethod = "__checkNull__";
            ClassBuilder<?> top = bldr.topLevel();
            if (!top.containsMethodNamed(nullCheckMethod)) {
                top.method(nullCheckMethod, mb -> {
                    mb.withModifier(STATIC, PRIVATE)
                            .addArgument("String", "name")
                            .addArgument("_X", "object")
                            .withTypeParam("_X")
                            .returning("_X")
                            .body(bb -> {
                                bb.ifNull("object").andThrow(nb -> {
                                    nb.withStringConcatentationArgument("Parameter ")
                                            .appendExpression("name")
                                            .append(" may not be null")
                                            .endConcatenation()
                                            .ofType("IllegalArgumentException");
                                }).endIf();
                                bb.returning("object");
                            });
                });
            }
        }
        return nullCheckMethod;
    }

    String failMethod() {
        if (failMethod == null) {
            failMethod = "__fail__";
            ClassBuilder<?> top = bldr.topLevel();
            if (!top.containsMethodNamed(failMethod)) {
                top.method(failMethod)
                        .withModifier(STATIC, PRIVATE)
                        .addArgument("String", "message")
                        .withTypeParam("_X")
                        .returning("_X")
                        .body()
                        .andThrow(nb -> {
                            nb.withArgument("message")
                                    .ofType("IllegalArgumentException");
                        }).endBlock();
            }
        }
        return failMethod;
    }

    ValidationMethodGenerator generator(FieldDescriptor fd) {
        return generators.computeIfAbsent(fd, this::create);
    }

    private ValidationMethodGenerator create(FieldDescriptor fd) {
        if (fd.isReallyPrimitive() && fd.constraints.isEmpty()) {
            return NoOpValidation.INSTANCE;
        }
        if (!fd.isPrimitive() && fd.optional && fd.nullValuesPermitted && fd.constraints.isEmpty()) {
            return NoOpValidation.INSTANCE;
        }
        if (fd.isPrimitive() && !fd.optional && fd.constraints.isEmpty()) {
            return NoOpValidation.INSTANCE;
        }
        return new DefaultGeneration(fd);
    }

    public interface ValidationMethodGenerator {

        Optional<String> validationMethod();

        default <T, V extends ValueExpressionBuilder<T>> T assign(String localVariableName, V value) {
            Optional<String> val = validationMethod();
            if (val.isPresent()) {
                return value.invoke(val.get()).withArgument(localVariableName)
                        .inScope();
            } else {
                return value.expression(localVariableName);
            }
        }
    }

    static class NoOpValidation implements ValidationMethodGenerator {

        private static final NoOpValidation INSTANCE = new NoOpValidation();

        @Override
        public Optional<String> validationMethod() {
            return Optional.empty();
        }

        @Override
        public <T, V extends ValueExpressionBuilder<T>> T assign(String localVariableName, V value) {
            return value.expression(localVariableName);
        }
    }

    class DefaultGeneration implements ValidationMethodGenerator {

        private final FieldDescriptor field;
        private String validationMethodName;

        public DefaultGeneration(FieldDescriptor field) {
            this.field = field;
        }

        @Override
        public Optional<String> validationMethod() {
            boolean noNullCheck
                    = field.isPrimitive()
                    || (field.optional && field.nullValuesPermitted);
            if (noNullCheck && field.constraints.isEmpty()) {
                return Optional.empty();
            }
            if (field.isPrimitive() && field.constraints.isEmpty()) {
                return Optional.empty();
            }

            if (validationMethodName == null) {
                validationMethodName = generate();
            }
            return Optional.ofNullable(validationMethodName);
        }

        private String generate() {
            String validationMethod = "__validate_" + capitalize(field.fieldName) + "__";
            ClassBuilder<?> top = bldr.topLevel();
            if (top.containsMethodNamed(validationMethod)) {
                return validationMethod;
            }
            top.method(validationMethod, mb -> {
                mb.withModifier(PRIVATE, STATIC);
                mb.addArgument(field.typeName(), field.fieldName);
                mb.returning(field.typeName());
                mb.docComment("Validates the parameter <code>" + field.fieldName
                        + "</code> of type <code>" + field.typeName() + "</code>.");
                boolean noNullCheck
                        = field.isPrimitive()
                        || (field.optional && field.nullValuesPermitted);
                List<String> gfc = desc.genericsRequiredFor(Collections.singleton(field));
                if (!gfc.isEmpty()) {
                    for (String g : gfc) {
                        mb.withTypeParam(desc.generics.nameWithBound(g));
                    }
                }
                List<ConstraintGenerator> cs = field.constraintsSorted();
                mb.body(bb -> {
                    if (!noNullCheck) {
//                        bb.ifNull(field.fieldName)
//                                .andThrow(nb -> {
//                                    nb.withStringConcatentationArgument(field.fieldName)
//                                            .append(" may not be null.")
//                                            .endConcatenation()
//                                            .ofType("IllegalArgumentException");
//                                }).endIf();
                        bb.invoke(nullCheckMethod())
                                .withStringLiteral(field.fieldName)
                                .withArgument(field.fieldName)
                                .inScope();

                        if (!cs.isEmpty()) {
                            ClassBuilder.IfBuilder<?> inn = bb.ifNotNull(field.fieldName);
                            for (ConstraintGenerator c : field.constraintsSorted()) {
                                c.decorateClass(top);
                                bb.lineComment(c + "");
                                c.generate(field.fieldName, top.className(), failMethod(), desc.utils(), inn, field.fieldName);
                            }
                            inn.endIf();
                        } else {
                            bb.lineComment("No constraints 1.");
                        }
                    } else if (!cs.isEmpty()) {
                        bb.lineComment("Have " + cs.size() + " constraintes");
                        for (ConstraintGenerator c : cs) {
                            bb.lineComment(c + "");
                            c.decorateClass(top);
                            c.generate(field.fieldName, top.className(),
                                    failMethod(), desc.utils(), bb, field.fieldName);
                        }
                    } else {
                        bb.lineComment("No null check needed");
                    }
                    if (field.canBeVarargs()) {
                        if (noNullCheck) {
                            bb.returningInvocationOf("copyOf").withArgument(field.fieldName)
                                    .withArgumentFromField("length").of(field.fieldName)
                                    .on("java.util.Arrays");
                        } else {
                            bb.returningValue().ternary()
                                    .isNull(field.fieldName)
                                    .endCondition().expression("null")
                                    .invoke("copyOf", ivb -> {
                                        ivb.withArgument(field.fieldName)
                                                .withArgumentFromField("length")
                                                .of(field.fieldName)
                                                .on("java.util.Arrays");
                                    });
                        }
                    } else {
                        bb.returning(field.fieldName);
                    }
                });
            });
            return validationMethod;

        }

    }
}
