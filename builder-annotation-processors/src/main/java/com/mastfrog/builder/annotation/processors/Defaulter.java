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
import com.mastfrog.builder.annotation.processors.spi.IsSetTestGenerator;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.java.vogon.ClassBuilder.ValueExpressionBuilder;
import com.mastfrog.util.strings.Escaper;
import com.mastfrog.util.strings.Strings;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/**
 * Provides default values for fields if they are optional and the annotation
 * telling us that provides information about what to use as a default.
 *
 * @author Tim Boudreau
 */
public abstract class Defaulter {

    abstract String defaultExpression();

    public boolean isNoOp() {
        return false;
    }

    public <X> X generate(String localName, IsSetTestGenerator test,
            ValueExpressionBuilder<X> veb, ClassBuilder<?> target) {
        return test.isSetTest(veb.ternary()).expression(localName).expression(defaultExpression());
    }

    public static Defaulter forAnno(Element el, TypeMirror type, AnnotationMirror nullableAnno,
            AnnotationUtils utils) {
        String stringDefault = utils.annotationValue(nullableAnno, "stringDefault", String.class);
        Double numericDefault = utils.annotationValue(nullableAnno, "numericDefault", Double.class);
        Boolean boolDefault = utils.annotationValue(nullableAnno, "booleanDefault", Boolean.class);
        Boolean useEmptyDefault = utils.annotationValue(nullableAnno, "defaulted", Boolean.class);

        int ct = countNonNulls(stringDefault, numericDefault, boolDefault, useEmptyDefault);
        if (ct > 1) {
            utils.fail("Only one of stringDefault, numericDefault, booleanDefault or "
                    + "defaulted may be specified per parameter", el, nullableAnno);
            return new NoOp();
        } else if (ct == 0) {
            return new NoOp();
        }

        TypeMirror rawType = utils.erasureOf(type);
        if (nonNullAndTrue(useEmptyDefault)) {
            return emptyDefault(el, type, nullableAnno, utils, rawType);
        } else if (nonNullAndTrue(boolDefault)) {
            if (rawType.getKind() != TypeKind.BOOLEAN && !"java.lang.Boolean".equals(rawType.toString())) {
                utils.fail("Cannot use a boolean default for " + rawType);
            }
            return new Fixed("true");
        } else if (boolDefault != null) {
            if (rawType.getKind() != TypeKind.BOOLEAN && !"java.lang.Boolean".equals(rawType.toString())) {
                utils.fail("Cannot use a boolean default for " + rawType);
            }
            return new Fixed("false");
        } else if (numericDefault != null) {
            switch (rawType.getKind()) {
                case DOUBLE:
                    return new NumberLiteral(numericDefault);
                case FLOAT:
                    return new NumberLiteral(numericDefault.floatValue());
                case BYTE:
                    return new NumberLiteral(numericDefault.byteValue());
                case INT:
                    return new NumberLiteral(numericDefault.intValue());
                case SHORT:
                    return new NumberLiteral(numericDefault.shortValue());
                case LONG:
                    return new NumberLiteral(numericDefault.longValue());
                case CHAR:
                    return new NumberGen(numericDefault, rawType.getKind());
                default:
                    switch (rawType.toString()) {
                        case "java.lang.Long":
                            return new NumberLiteral(numericDefault.longValue());
                        case "java.lang.Integer":
                            return new NumberLiteral(numericDefault.intValue());
                        case "java.lang.Float":
                            return new NumberLiteral(numericDefault.floatValue());
                        case "java.lang.Short":
                            return new NumberLiteral(numericDefault.shortValue());
                        case "java.lang.Byte":
                            return new NumberLiteral(numericDefault.byteValue());
                        case "java.lang.Double":
                            return new NumberLiteral(numericDefault);
                        case "java.lang.Character":
                            return new NumberGen(numericDefault, rawType.getKind());
                        default:
                            TypeElement te = utils.processingEnv().getElementUtils().getTypeElement(rawType.toString());
                            if (te != null) {

                                ExecutableElement numConstruct
                                        = findNumericConstructor(te, utils);
                                if (numConstruct != null) {
                                    return new NumberConstructorDefaulter(te, convertNumberFor(numConstruct, numericDefault));
                                }

                                utils.fail("Cannot use a numeric default " + numericDefault
                                        + " for a variable of type " + type);
                            }
                    }
                    break;
            }
        } else if (stringDefault != null) {
            if (utils.isAssignable(rawType, Enum.class.getName())) {
                TypeElement enumType = utils.processingEnv().getElementUtils().getTypeElement(rawType.toString());
                if (enumType != null) {
                    boolean found = false;
                    List<String> names = new ArrayList<>();
                    for (Element e : enumType.getEnclosedElements()) {
                        if (e.getKind() == ElementKind.ENUM_CONSTANT) {
                            String name = e.getSimpleName().toString();
                            names.add(name);
                            if (name.equals(stringDefault)) {
                                found = true;
                                break;
                            }
                            names.add(name);
                        }
                    }
                    if (!found) {
                        utils.fail("No enum constant named '" + stringDefault + "' in "
                                + enumType.getSimpleName() + ".  Available constants are "
                                + Strings.join(", ", names),
                                el, nullableAnno);
                    }
                }
                return new EnumLiteral(rawType, stringDefault);
            } else if (java.lang.CharSequence.class.getName().equals(rawType.toString())
                    || utils.isAssignable(rawType, "java.lang.String")) {
                return new StringLiteral(stringDefault);
            } else if ("char".equals(rawType.toString())
                    || "java.lang.Character".equals(rawType.toString())) {
                if (stringDefault.length() != 1) {
                    utils.fail("Character default for " + el.getSimpleName()
                            + " must be exactly 1 character in length", el, nullableAnno);
                } else {
                    return new CharLiteral(stringDefault.charAt(0));
                }
            } else {
                TypeElement te = utils.processingEnv().getElementUtils().getTypeElement(rawType.toString());
                ExecutableElement stringConstructor = findStringConstructor(te, utils);
                if (stringConstructor != null) {
                    return new StringConstructorDefaulter(te, stringDefault);
                }
                ExecutableElement factoryMethod = findStringFactoryMethod(te, utils);
                if (factoryMethod != null) {
                    return new StringFactoryMethodDefaulter(te,
                            factoryMethod.getSimpleName().toString(), stringDefault);
                }

                utils.fail("Cannot use a string default to create a parameter of type " + type);
            }
        }
        return new NoOp();
    }

    static ExecutableElement findNumericConstructor(TypeElement te, AnnotationUtils utils) {
        if (te == null) {
            return null;
        }
        ExecutableElement el = findConstructor(te, utils, "double", "java.lang.Double");
        if (el == null) {
            el = findConstructor(te, utils, "float", "java.lang.Float");
        }
        if (el == null) {
            el = findConstructor(te, utils, "double", "java.lang.Double");
        }
        if (el == null) {
            el = findConstructor(te, utils, "long", "java.lang.Long");
        }
        if (el == null) {
            el = findConstructor(te, utils, "int", "java.lang.Integer");
        }
        if (el == null) {
            el = findConstructor(te, utils, "short", "java.lang.Short");
        }
        if (el == null) {
            el = findConstructor(te, utils, "byte", "java.lang.Byte");
        }
        return el;
    }

    static Number convertNumberFor(ExecutableElement el, Number orig) {
        TypeMirror type = el.getParameters().get(0).asType();
        switch (type.getKind()) {
            case DOUBLE:
                return orig.doubleValue();
            case FLOAT:
                return orig.floatValue();
            case LONG:
                return orig.longValue();
            case INT:
                return orig.intValue();
            case SHORT:
                return orig.shortValue();
            case BYTE:
                return orig.byteValue();
        }
        switch (type.toString()) {
            case "java.lang.Double":
                return orig.doubleValue();
            case "java.lang.Float":
                return orig.floatValue();
            case "java.lang.Long":
                return orig.longValue();
            case "java.lang.Integer":
                return orig.intValue();
            case "java.lang.Short":
                return orig.shortValue();
            case "java.lang.Byte":
                return orig.byteValue();
        }

        return orig;
    }

    static ExecutableElement findConstructor(TypeElement te, AnnotationUtils utils, String... typeNames) {
        if (te == null) {
            return null;
        }
        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement ex = (ExecutableElement) e;
                List<? extends VariableElement> params = ex.getParameters();
                if (params.size() == 1) {
                    VariableElement ve = params.get(0);
                    for (String t : typeNames) {
                        if (t.equals(ve.asType().toString()) || utils.isAssignable(ve.asType(), t)) {
                            return ex;
                        }
                    }
                }
            }
        }
        return null;
    }

    static ExecutableElement findStringConstructor(TypeElement te, AnnotationUtils utils) {
        if (te == null) {
            return null;
        }
        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement ex = (ExecutableElement) e;
                List<? extends VariableElement> params = ex.getParameters();
                if (params.size() == 1) {
                    VariableElement ve = params.get(0);
                    if ("java.lang.String".equals(ve.asType().toString()) || utils.isAssignable(ve.asType(), "java.lang.CharSequence")) {
                        return ex;
                    }
                }
            }
        }
        return null;
    }

    static ExecutableElement findStringFactoryMethod(TypeElement te, AnnotationUtils utils) {
        if (te == null) {
            return null;
        }
        for (Element e : te.getEnclosedElements()) {
            if (e.getKind() == ElementKind.METHOD) {
                ExecutableElement ex = (ExecutableElement) e;
                if (ex.getModifiers().contains(Modifier.STATIC) && te.asType().equals(ex.getReturnType())) {
                    List<? extends VariableElement> params = ex.getParameters();
                    if (params.size() == 1) {
                        VariableElement ve = params.get(0);
                        if ("java.lang.String".equals(ve.asType().toString()) || utils.isAssignable(ve.asType(), "java.lang.CharSequence")) {
                            return ex;
                        }
                    }
                }
            }
        }
        return null;
    }

    static class NumberGen extends Defaulter {

        private final Number numericDefault;
        private final TypeKind kind;

        public NumberGen(Number numericDefault, TypeKind kind) {
            this.numericDefault = numericDefault;
            this.kind = kind;
        }

        @Override
        String defaultExpression() {
            switch (kind) {
                case DOUBLE:
                    if (numericDefault.doubleValue() == Double.MAX_VALUE) {
                        return "Double.MAX_VALUE";
                    } else if (numericDefault.doubleValue() == Double.MIN_VALUE) {
                        return "Double.MIN_VALUE";
                    } else if (numericDefault.doubleValue() == Double.MIN_NORMAL) {
                        return "Double.MIN_NORMAL";
                    }
                    return numericDefault + "D";
                case FLOAT:
                    if (numericDefault.floatValue() == Float.MIN_VALUE) {
                        return "Float.MIN_VALUE";
                    } else if (numericDefault.floatValue() == Float.MAX_VALUE) {
                        return "Float.MAX_VALUE";
                    }
                    return numericDefault.floatValue() + "F";
                case BYTE:
                    if (numericDefault.byteValue() == Byte.MIN_VALUE) {
                        return "Byte.MIN_VALUE";
                    } else if (numericDefault.byteValue() == Byte.MAX_VALUE) {
                        return "Byte.MAX_VALUE";
                    }
                    return "(byte) " + numericDefault.byteValue();
                case INT:
                    if (numericDefault.intValue() == Integer.MAX_VALUE) {
                        return "Integer.MAX_VALUE";
                    } else if (numericDefault.intValue() == Integer.MIN_VALUE) {
                        return "Integer.MIN_VALUE";
                    }
                    return numericDefault.intValue() + "";
                case SHORT:
                    if (numericDefault.shortValue() == Short.MAX_VALUE) {
                        return "Short.MAX_VALUE";
                    } else if (numericDefault.shortValue() == Short.MIN_VALUE) {
                        return "Short.MIN_VALUE";
                    }
                    return "(short) " + numericDefault.shortValue();
                case LONG:
                    if (numericDefault.longValue() == Long.MAX_VALUE) {
                        return "Long.MAX_VALUE";
                    } else if (numericDefault.longValue() == Long.MIN_VALUE) {
                        return "Long.MIN_VALUE";
                    }
                    return numericDefault.longValue() + "L";
                case CHAR:
                    char cc = (char) numericDefault.intValue();
                    if (cc == Character.MIN_VALUE) {
                        return "Character.MIN_VALUE";
                    } else if (cc == Character.MAX_VALUE) {
                        return "Character.MAX_VALUE";
                    }
                    return "(char) " + (numericDefault.shortValue() & 0xFFFF);
                default:
                    throw new AssertionError(kind);
            }
        }
    }

    private static Defaulter emptyDefault(Element el, TypeMirror type,
            AnnotationMirror nullableAnno, AnnotationUtils utils, TypeMirror rawType) {
        boolean arr = rawType.getKind() == TypeKind.ARRAY;
        if (!arr) {
            switch (type.getKind()) {
                case BOOLEAN:
                    return new Fixed("false");
                case INT:
                    return new Fixed("0");
                case BYTE:
                    return new Fixed("0x0");
                case CHAR:
                    return new Fixed("(char) 0");
                case DOUBLE:
                    return new Fixed("0D");
                case SHORT:
                    return new Fixed("(short) 0");
                case FLOAT:
                    return new Fixed("0F");
                case LONG:
                    return new Fixed("0L");
                case VOID:
                case NULL:
                    return new Fixed("null");
            }
            if (rawType.toString().equals("java.util.ArrayList")) {
                return new Fixed("new java.util.ArrayList<>(1)");
            } else if (rawType.toString().equals("java.util.HashSet")) {
                return new Fixed("new java.util.HashSet<>(1)");
            } else if (rawType.toString().equals("java.util.TreeSet")) {
                return new Fixed("new java.util.TreeSet<>()");
            } else if (rawType.toString().equals("java.util.LinkedList")) {
                return new Fixed("new java.util.LinkedList<>()");
            } else if (rawType.toString().equals("java.util.HashMap")) {
                return new Fixed("new java.util.HashMap<>()");
            } else if (rawType.toString().equals("java.util.TreeMap")) {
                return new Fixed("new java.util.TreeMap<>()");
            } else if (utils.isAssignable(rawType, "java.util.List")) {
                return new Fixed("java.util.Collections.emptyList()");
            } else if (utils.isAssignable(rawType, "java.util.NavigableMap")) {
                return new Fixed("java.util.Collections.emptyNavigableMap()");
            } else if (utils.isAssignable(rawType, "java.util.NavigableSet")) {
                return new Fixed("java.util.Collections.emptyNavigableSet()");
            } else if (utils.isAssignable(rawType, "java.util.SortedSet")) {
                return new Fixed("java.util.Collections.emptySortedSet()");
            } else if (utils.isAssignable(rawType, "java.util.SortedMap")) {
                return new Fixed("java.util.Collections.emptySortedMap()");
            } else if (utils.isAssignable(rawType, "java.util.ListIterator")) {
                return new Fixed("java.util.Collections.emptyListIterator()");
            } else if (utils.isAssignable(rawType, "java.util.Iterator")) {
                return new Fixed("java.util.Collections.emptyIterator()");
            } else if (utils.isAssignable(rawType, "java.util.Enumeration")) {
                return new Fixed("java.util.Collections.emptyEnumeration()");
            } else if (utils.isAssignable(rawType, "java.util.Set")) {
                return new Fixed("java.util.Collections.emptySet()");
            } else if (utils.isAssignable(rawType, "java.util.Map")) {
                return new Fixed("java.util.Collections.emptyMap()");
            } else if (utils.isAssignable(rawType, "java.util.Collection")
                    || "java.lang.Iterable".equals(rawType.toString())) {
                return new Fixed("java.util.Collections.emptySet()");
            } else if (utils.isAssignable(rawType, "java.lang.String")
                    || "java.lang.CharSequence".equals(rawType.toString())) {
                return new EmptyString();
            } else if (utils.isAssignable(rawType, "java.util.Optional")) {
                return new Fixed("java.util.Optional.empty()");
            } else if (utils.isAssignable(rawType, "java.util.Locale")) {
                return new Fixed("java.util.Locale.getDefault()");
            } else if (utils.isAssignable(rawType, "java.nio.charset.Charset")) {
                return new Fixed("java.nio.charset.StandardCharsets.UTF_8");
            } else if (utils.isAssignable(rawType, "java.time.ZoneId")) {
                return new Fixed("java.time.ZoneId.of(\"GMT\")");
            } else if (utils.isAssignable(rawType, "java.time.Duration")) {
                return new Fixed("java.time.Duration.ZERO");
            } else if (utils.isAssignable(rawType, "java.lang.StringBuilder")) {
                return new Fixed("new StringBuilder()");
            }
        } else {
            ArrayType at = (ArrayType) rawType;
            TypeMirror comp = at.getComponentType();
            switch (comp.getKind()) {
                case ARRAY:
                    utils.fail("Multi-dimensional array empty defaults not supported", el, nullableAnno);
                    return new NoOp();
                case BOOLEAN:
                    return new Fixed("new boolean[0]");
                case INT:
                    return new Fixed("new int[0]");
                case LONG:
                    return new Fixed("new long[0]");
                case BYTE:
                    return new Fixed("new byte[0]");
                case CHAR:
                    return new Fixed("new char[0]");
                case DOUBLE:
                    return new Fixed("new double[0]");
                case FLOAT:
                    return new Fixed("new float[0]");
                case SHORT:
                    return new Fixed("new short[0]");
                case DECLARED:
                    return new Fixed("new " + comp + "[0]");
                case TYPEVAR:
                    return new GenericArray();
                default:
                    utils.fail("Cannot create a default array value for component of kind "
                            + comp.getKind(), el, nullableAnno);
            }
        }
        utils.fail("No idea how to make an empty default value for " + type, el, nullableAnno);
        return new NoOp();
    }

    private static boolean nonNullAndTrue(Boolean val) {
        return val != null && val;
    }

    private static int countNonNulls(Object... parts) {
        int result = 0;
        for (Object o : parts) {
            if (o != null) {
                result++;
            }
        }
        return result;
    }

    static class NoOp extends Defaulter {

        @Override
        String defaultExpression() {
            return "null";
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb,
                ClassBuilder<?> target) {
            return veb.expression(localName);
        }

        public boolean isNoOp() {
            return true;
        }
    }

    static class GenericArray extends Defaulter {

        @Override
        String defaultExpression() {
            return "null";
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb,
                ClassBuilder<?> target) {
            String mn = "__emptyGenericArray__";
            ClassBuilder<?> top = target.topLevel();
            if (!top.containsMethodNamed(mn)) {
                top.method(mn, mb -> {
                    mb.withModifier(Modifier.PRIVATE, Modifier.STATIC)
                            .returning("T[]")
                            .docComment("Works around the prohibition on explicitly creating new "
                                    + "\narrays of a type parameter or a parameterized type by the compiler.")
                            .annotatedWith("SafeVarargs").closeAnnotation()
                            .withTypeParam("T")
                            .addVarArgArgument("T", "_empty")
                            .lineComment("// " + Long.MAX_VALUE)
                            .iff(cond -> {
                                cond.literal(0).isEqualTo().field("length").of("_empty")
                                        .andThrow(nb -> {
                                            nb.withStringConcatentationArgument(localName)
                                                    .append(" may not be null")
                                                    .endConcatenation()
                                                    .ofType("IllegalArgumentException");
                                        });
                            })
                            .returning("_empty").endBlock();
                });
            }
            return test.isSetTest(veb.ternary()).expression(localName).invoke(mn).on(top.className());
        }

        public boolean isNoOp() {
            return true;
        }
    }

    static class Fixed extends Defaulter {

        private final String expr;

        public Fixed(String expr) {
            this.expr = expr;
        }

        @Override
        String defaultExpression() {
            return expr;
        }
    }

    static class EmptyString extends Defaulter {

        @Override
        String defaultExpression() {
            return "\"\"";
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb,
                ClassBuilder<?> target) {
            return test.isSetTest(veb.ternary()).expression(localName).literal("");
        }
    }

    static class StringLiteral extends Defaulter {

        private final String string;

        public StringLiteral(String string) {
            this.string = string;
        }

        @Override
        String defaultExpression() {
            return '"' + string + '"';
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb,
                ClassBuilder<?> target) {
            return test.isSetTest(veb.ternary()).expression(localName).literal(string);
        }
    }

    static class CharLiteral extends Defaulter {

        private final char ch;

        public CharLiteral(char ch) {
            this.ch = ch;
        }

        @Override
        String defaultExpression() {
            if (ch == '\'') {
                return "'\\'\\'";
            }
            return '\'' + Character.toString(ch) + '\'';
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb,
                ClassBuilder<?> target) {
            return test.isSetTest(veb.ternary()).expression(localName).literal(ch);
        }
    }

    static class EnumLiteral extends Defaulter {

        private final TypeMirror type;
        private final String literal;

        public EnumLiteral(TypeMirror type, String literal) {
            this.type = type;
            this.literal = literal;
        }

        @Override
        String defaultExpression() {
            return type + "." + literal;
        }

        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb,
                ClassBuilder<?> target) {
            return test.isSetTest(veb.ternary()).expression(localName).expression(defaultExpression());
        }
    }

    static class StringConstructorDefaulter extends Defaulter {

        private final TypeElement ofType;
        private final String string;

        public StringConstructorDefaulter(TypeElement ofType, String string) {
            this.ofType = ofType;
            this.string = string;
        }

        @Override
        String defaultExpression() {
            return "new " + ofType.getQualifiedName() + "(\""
                    + Strings.escape(string, JAVA_CODE)
                    + "\")";
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb, ClassBuilder<?> target) {
            return test.isSetTest(veb.ternary()).expression(localName).toNewInstance().withStringLiteral(string).ofType(ofType.getQualifiedName().toString());
        }
    }

    static class NumberConstructorDefaulter extends Defaulter {

        private final TypeElement ofType;
        private final Number num;

        public NumberConstructorDefaulter(TypeElement ofType, Number num) {
            this.ofType = ofType;
            this.num = num;
        }

        private String numString() {
            if (num instanceof Long) {
                return num.longValue() + "L";
            } else if (num instanceof Integer) {
                return Integer.toString(num.intValue());
            } else if (num instanceof Double) {
                return num + "D";
            } else if (num instanceof Float) {
                return num + "F";
            } else if (num instanceof Short) {
                return "(short) " + num;
            } else if (num instanceof Byte) {
                return "(byte) " + num;
            }
            return num.toString();
        }

        @Override
        String defaultExpression() {
            return "new " + ofType.getQualifiedName() + "(" + numString() + ")";
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb, ClassBuilder<?> target) {
            return test.isSetTest(veb.ternary()).expression(localName).toNewInstance().withArgument(ClassBuilder.number(num)).ofType(ofType.getQualifiedName().toString());
        }
    }

    static class StringFactoryMethodDefaulter extends Defaulter {

        private final TypeElement ofType;
        private final String simpleName;
        private final String string;

        public StringFactoryMethodDefaulter(TypeElement ofType, String simpleName, String string) {
            this.ofType = ofType;
            this.simpleName = simpleName;
            this.string = string;
        }

        @Override
        String defaultExpression() {
            return ofType.getQualifiedName() + "." + simpleName + "(\""
                    + Strings.escape(string, JAVA_CODE)
                    + "\")";
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb, ClassBuilder<?> target) {
            target.importing(ofType.getQualifiedName().toString());
            return test.isSetTest(veb.ternary()).expression(localName).invoke(simpleName)
                    .withStringLiteral(string).on(ofType.getSimpleName().toString());
        }

    }

    static Escaper JAVA_CODE = ch -> {
        switch (ch) {
            case '\t':
                return "\\t";
            case '"':
                return "\\\"";
            case '\b':
                return "\\b";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\f':
                return "\\f";
            case '\\':
                return "\\\\";
            default:
                return Character.toString(ch);
        }
    };

    static class NumberLiteral extends Defaulter {

        private final Number value;

        public NumberLiteral(Number value) {
            this.value = value;
        }

        public String defaultExpression() {
            if (value instanceof Byte) {
                return "(byte) " + value.byteValue();
            } else if (value instanceof Double) {
                return value.doubleValue() + "D";
            } else if (value instanceof Float) {
                return value.floatValue() + "F";
            } else if (value instanceof Short) {
                return "(short) " + value;
            } else if (value instanceof Long) {
                return value + "L";
            }
            return value.toString();
        }

        @Override
        public <X> X generate(String localName, IsSetTestGenerator test, ValueExpressionBuilder<X> veb,
                ClassBuilder<?> target) {
            return test.isSetTest(veb.ternary()).expression(localName).literal(value);
        }

    }
}
