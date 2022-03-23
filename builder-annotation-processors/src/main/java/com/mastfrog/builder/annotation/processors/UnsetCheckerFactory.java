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
import com.mastfrog.builder.annotation.processors.LocalFieldFactory.LocalFieldGenerator;
import com.mastfrog.builder.annotation.processors.spi.IsSetTestGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.BlockBuilderBase;
import com.mastfrog.java.vogon.ClassBuilder.ConditionBuilder;
import com.mastfrog.java.vogon.ClassBuilder.SwitchBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ValueExpressionBuilder;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.lang.model.element.Modifier;

/**
 *
 * @author Tim Boudreau
 */
public class UnsetCheckerFactory<C> {

    private final ClassBuilder<C> bldr;
    private final Set<BuilderStyles> styles;
    private final BuilderDescriptor desc;
    private final Map<FieldDescriptor, UnsetCheckGenerator> fieldGens = new TreeMap<>();
    private String failMethod;
    private MaskField mask;
    private final Function<FieldDescriptor, LocalFieldGenerator> localFields;

    UnsetCheckerFactory(ClassBuilder<C> bldr, Set<BuilderStyles> styles, BuilderDescriptor desc,
            Function<FieldDescriptor, LocalFieldGenerator> localFields) {
        this.bldr = bldr;
        this.styles = styles;
        this.desc = desc;
        this.localFields = localFields;
    }

    static <C> UnsetCheckerFactory<C> create(ClassBuilder<C> bldr,
            BuilderDescriptor desc, LocalFieldFactory<C> factory) {
        return new UnsetCheckerFactory<>(bldr, desc.styles, desc, factory::generatorFor);
    }

    private String failMethod() {
        if (failMethod == null) {
            failMethod = bldr.unusedMethodName("fail");
            bldr.method(failMethod)
                    .withModifier(Modifier.PRIVATE, Modifier.STATIC)
                    .addArgument("String", "failure")
                    .body(bb -> {
                        bb.andThrow(nb -> {
                            nb.withArgument("failure")
                                    .ofType("IllegalArgumentException");
                        });
                    });
        }
        return failMethod;
    }

    public UnsetCheckGenerator generatorFor(FieldDescriptor fd) {
        return fieldGens.computeIfAbsent(fd, this::create);
    }

    private int pfCount = -1;

    private int requiredPrimitiveCount() {
        if (pfCount != -1) {
            return pfCount;
        }
        int ct = 0;
        for (FieldDescriptor fd : desc.requiredFields()) {
            if (fd.isPrimitive()) {
                ct++;
            }
        }
        return pfCount = ct;
    }

    private UnsetCheckGenerator create(FieldDescriptor fd) {
        if (fd.isPrimitive() && !fd.optional) {
            if (requiredPrimitiveCount() == 1) {
                return new SinglePrimitiveUnsetGenerator(fd);
            }
            return new RequiredPrimitiveUnsetCheckGenerator(fd);
        } else if (!fd.isPrimitive() && !fd.optional) {
            return new ObjectUnsetCheckGenerator(fd);
        }
        return new NoOpUnsetGenerator(fd);
    }

    public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generateCheck(
            FieldDescriptor fd, B bb) {
        generatorFor(fd).generate(bb, bldr.className(), this::failMethod);
    }

    public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generateAllChecks(
            B bb, String pn, String addMethod) {
        Set<FieldDescriptor> primitives = new TreeSet<>();
        Set<FieldDescriptor> nonPrimitives = new TreeSet<>();
        for (FieldDescriptor fd : desc.fields()) {
            if (!fd.optional && fd.isPrimitive()) {
                primitives.add(fd);
            } else {
                nonPrimitives.add(fd);
            }
        }
        if (primitives.size() == 1) {
            nonPrimitives.addAll(primitives);
        } else if (!primitives.isEmpty()) {
            long mask = 0;
            for (int i = 0; i < primitives.size(); i++) {
                mask |= 1L << i;
            }
            ClassBuilder.IfBuilder<B> iff = bb.iff()
                    .booleanExpression(mask().name() + " != " + mask().fieldType().toExpression(mask));
            for (FieldDescriptor fd : primitives) {
                generatorFor(fd).generate(iff, pn, () -> addMethod);
            }
            iff.endIf();
        }
        for (FieldDescriptor fd : nonPrimitives) {
            generatorFor(fd).generate(bb, pn, () -> addMethod);
        }
    }

    MaskField mask() {
        return mask == null ? mask = new MaskField() : mask;
    }

    public interface UnsetCheckGenerator extends IsSetTestGenerator {

        <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> boolean generate(
                B bb,
                String problemsHolder, Supplier<String> addProblemMethodName);

        <T> SwitchBuilder<T> generateSwitchTest(SwitchBuilder<T> sw);

        default <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> B onSet(B bb) {
            return bb;
        }

        default boolean usesMaskField() {
            return false;
        }
    }

    private class SinglePrimitiveUnsetGenerator implements UnsetCheckGenerator {

        private final FieldDescriptor field;
        private String isSetField;

        public SinglePrimitiveUnsetGenerator(FieldDescriptor field) {
            this.field = field;
        }

        private String isSetFieldName() {
            if (isSetField == null) {
                isSetField = bldr.unusedFieldName("is" + capitalize(field.fieldName) + "Set");
                bldr.field(isSetField).withModifier(Modifier.PRIVATE).ofType("boolean");
            }
            return isSetField;
        }

        @Override
        public <T, B extends BlockBuilderBase<T, B, X>, X> B onSet(B bb) {
            bb.statement("this." + isSetFieldName() + " = true");
            return bb;
        }

        public <X> ValueExpressionBuilder<ValueExpressionBuilder<X>> isSetTest(
                ConditionBuilder<ValueExpressionBuilder<ValueExpressionBuilder<X>>> tern) {
            return tern.booleanExpression(isSetFieldName());
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> boolean generate(
                B bb, String problemsHolder, Supplier<String> addProblemMethodName) {
            bb.iff().booleanExpression("!this." + isSetFieldName())
                    .invoke(addProblemMethodName.get())
                    .withStringConcatentationArgument(field.typeName())
                    .append(" parameter '").append(field.fieldName)
                    .append("' is unset")
                    .endConcatenation().on(problemsHolder)
                    .endIf();
            return true;
        }

        @Override
        public <T> SwitchBuilder<T> generateSwitchTest(SwitchBuilder<T> sw) {
            return sw.inCase(field.fieldName).returningField(isSetFieldName()).ofThis().endBlock();
        }
    }

    private class AbstractUnsetGenerator {

        private int primitiveFieldIndex = -1;
        protected final FieldDescriptor field;

        public AbstractUnsetGenerator(FieldDescriptor field) {
            this.field = field;
            add();
        }

        protected void add() {
        }

        public String fieldName() {
            return field.fieldName;
        }

        public String typeName() {
            return field.typeName();
        }

        protected String localFieldName() {
            return localFields.apply(field).localFieldName();
        }

        protected int primitiveFieldIndex() {
            if (primitiveFieldIndex == -1) {
                primitiveFieldIndex = mask().nextPrimitiveField();
            }
            return primitiveFieldIndex;
        }

        public <X> ValueExpressionBuilder<ValueExpressionBuilder<X>> isSetTest(
                ConditionBuilder<ValueExpressionBuilder<ValueExpressionBuilder<X>>> tern) {
            return tern.isNotNull(localFieldName()).endCondition();
        }
    }

    private class ObjectUnsetCheckGenerator extends AbstractUnsetGenerator implements UnsetCheckGenerator {

        public ObjectUnsetCheckGenerator(FieldDescriptor field) {
            super(field);
        }

        @Override
        public <T, B extends BlockBuilderBase<T, B, X>, X> boolean generate(B bb, String problemsHolder, Supplier<String> addProblemMethodName) {
            bb.ifNull(localFieldName())
                    .invoke(addProblemMethodName.get())
                    .withStringConcatentationArgument(typeName())
                    .append(" parameter ")
                    .append('\'').append(fieldName()).append('\'')
                    .append(" is unset")
                    .endConcatenation()
                    .on(problemsHolder)
                    .endIf();
            return true;
        }

        @Override
        public <T> SwitchBuilder<T> generateSwitchTest(SwitchBuilder<T> sw) {
            return sw.inCase(fieldName())
                    .returningBoolean()
                    .isNotNull(localFieldName()).endCondition().endBlock();
        }
    }

    private class RequiredPrimitiveUnsetCheckGenerator extends AbstractUnsetGenerator implements UnsetCheckGenerator {

        public RequiredPrimitiveUnsetCheckGenerator(FieldDescriptor field) {
            super(field);
        }

        @Override
        public <T, B extends BlockBuilderBase<T, B, X>, X> boolean generate(
                B bb, String problemsHolder, Supplier<String> addProblemMethodName) {
            String maskField = mask().name();
            String fn = localFieldName();
            bb.iff().booleanExpression(testExpression())
                    .invoke(addProblemMethodName.get())
                    .withStringConcatentationArgument(typeName())
                    .append(" parameter ")
                    .append(fieldName())
                    .append(" is unset")
                    .endConcatenation()
                    .on(problemsHolder)
                    .endIf();
            return true;
        }

        @Override
        protected void add() {
            mask().add();
        }

        @Override
        public <T, B extends BlockBuilderBase<T, B, X>, X> B onSet(B bb) {
            bb.statement(mask().name() + " |= " + mask().fieldType().toExpression(1L << primitiveFieldIndex()));
            return bb;
        }

        public <X> ValueExpressionBuilder<ValueExpressionBuilder<X>> isSetTest(
                ConditionBuilder<ValueExpressionBuilder<ValueExpressionBuilder<X>>> tern) {
            return tern.booleanExpression(testExpression().replaceAll("==", "!="));
        }

        private String testExpression() {
            return "(" + mask().name() + " & "
                    + (mask().fieldType().toExpression(1 << primitiveFieldIndex())) + ") == 0";
        }

        @Override
        public <T> SwitchBuilder<T> generateSwitchTest(SwitchBuilder<T> sw) {
            return sw.inCase(fieldName(), cs -> {
                String exp = testExpression().replaceAll("==", "!=");
                cs.returning(exp);
            });
        }

        @Override
        public boolean usesMaskField() {
            return true;
        }
    }

    private class NoOpUnsetGenerator extends AbstractUnsetGenerator implements UnsetCheckGenerator {

        public NoOpUnsetGenerator(FieldDescriptor field) {
            super(field);
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> boolean generate(B bb,
                String problemsHolder, Supplier<String> addProblemMethodName) {
            return false;
        }

//        @Override
//        public <X> ValueExpressionBuilder<ValueExpressionBuilder<X>> isSetTest(ConditionBuilder<ValueExpressionBuilder<ValueExpressionBuilder<X>>> tern) {
//            throw new UnsupportedOperationException("Should not be performing an unset test on " + field
//                + " of type " + field.typeName() + " optional? " + field.optional);
//        }

        @Override
        public <T> SwitchBuilder<T> generateSwitchTest(SwitchBuilder<T> sw) {
            return sw;
        }
    }

    enum IntegerFieldType {
        BYTE, SHORT, INT, LONG;

        private int bits() {
            switch (this) {
                case BYTE:
                    return Byte.SIZE;
                case SHORT:
                    return Short.SIZE;
                case INT:
                    return Integer.SIZE;
                case LONG:
                    return Long.SIZE;
                default:
                    throw new AssertionError(this);
            }
        }

        public static IntegerFieldType forBits(int bits) {
            for (IntegerFieldType tp : values()) {
                if (bits <= tp.bits()) {
                    return tp;
                }
            }
            throw new IllegalArgumentException("Cannot handle more than "
                    + LONG.bits() + " primitive fields");
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        private String cast() {
            return "(" + toString() + ") ";
        }

        public String toExpression(Number n) {
            switch (this) {
                case BYTE:
                    return cast() + n.byteValue();
                case SHORT:
                    return cast() + n.shortValue();
                case INT:
                    return Integer.toString(n.intValue());
                case LONG:
                    return n.longValue() + "L";
                default:
                    throw new AssertionError();
            }
        }
    }

    private class MaskField {

        private String primitiveMaskField;
        private int nextFieldIndex = -1;
        private final IntegerFieldType maskFieldType;
        private final int count;
        private boolean added;

        MaskField() {
            int count = 0;
            for (FieldDescriptor fd : desc.fields()) {
                if (fd.isPrimitive() && !fd.optional) {
                    count++;
                }
            }
            this.count = count;
            maskFieldType = IntegerFieldType.forBits(count);
        }

        public void add() {
            if (!added) {
                added = true;
                bldr.field(name())
                        .withModifier(Modifier.PRIVATE)
                        .ofType(fieldType().toString());
            }
        }

        public IntegerFieldType fieldType() {
            return maskFieldType;
        }

        public int nextPrimitiveField() {
            return ++nextFieldIndex;
        }

        public String name() {
            return primitiveMaskField == null
                    ? primitiveMaskField = bldr.unusedFieldName("primitiveFields")
                    : primitiveMaskField;
        }
    }
}
