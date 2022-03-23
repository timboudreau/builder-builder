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
package com.mastfrog.builder.annotation.processors.builtinconstraints;

import com.mastfrog.annotation.AnnotationUtils;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import static com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator.NULLABLE_ANNOTATION;
import com.mastfrog.builder.annotation.processors.spi.ConstraintHandler;
import com.mastfrog.java.vogon.ClassBuilder;
import com.mastfrog.util.service.ServiceProvider;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

/**
 *
 * @author Tim Boudreau
 */
@ServiceProvider(ConstraintHandler.class)
public class StringPatternHandler implements ConstraintHandler {

    private static final String STRING_PATTERN = "com.mastfrog.builder.annotations.constraint.StringPattern";

    @Override
    public void collect(AnnotationUtils utils, Element targetElement, VariableElement parameterElement, Consumer<ConstraintGenerator> genConsumer) {
        AnnotationMirror mir = utils.findAnnotationMirror(parameterElement, STRING_PATTERN);
        if (mir != null) {
            boolean nullable = utils.findAnnotationMirror(parameterElement, NULLABLE_ANNOTATION) != null;
            genConsumer.accept(new StringPatternConstraintGenerator(utils, parameterElement, mir, nullable));
        }
    }

    static final class StringPatternConstraintGenerator implements ConstraintGenerator {

        private final Pattern pattern;
        private final int minLength;
        private final int maxLength;
        private final String varName;
        private final boolean nullable;

        StringPatternConstraintGenerator(AnnotationUtils utils, VariableElement ve, AnnotationMirror mir, boolean nullable) {
            this.varName = ve.getSimpleName().toString();
            String pat = utils.annotationValue(mir, "value", String.class, ".*");
            minLength = utils.annotationValue(mir, "minLength", Integer.class, 0);
            maxLength = utils.annotationValue(mir, "maxLength", Integer.class, 0);
            if (!".*".equals(pat)) {
                Pattern p = null;
                try {
                    p = Pattern.compile(pat);
                } catch (Exception | Error e) {
                    utils.fail("Invalid regular expression '" + pat + "'", ve, mir);
                }
                pattern = p;
            } else {
                pattern = null;
            }
            if (minLength > maxLength) {
                utils.fail("Max length " + maxLength + " is less than min length "
                        + minLength, ve, mir);
            }
            if (minLength < 0) {
                utils.fail("Min length " + minLength + " is less than zero.", ve, mir);
            }
            if (maxLength < 0) {
                utils.fail("Max length " + maxLength + " is less than zero.", ve, mir);
            }
            this.nullable = nullable;
        }

        @Override
        public int weight() {
            int result = 0;
            if (pattern != null) {
                result += 250;
            }
            if (minLength != 0) {
                result += 50;
            }
            if (maxLength != Integer.MAX_VALUE) {
                result += 50;
            }
            return result;
        }

        private String patternVarName() {
            return "_" + varName + "Pattern";
        }

        @Override
        public <C> void decorateClass(ClassBuilder<C> bldr) {
            if (pattern != null) {
                bldr.importing(Pattern.class);
                if (!bldr.topLevel().containsFieldNamed(patternVarName())) {
                    bldr.topLevel().field(patternVarName(), fb -> {
                        fb.withModifier(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                                .initializedFromInvocationOf("compile")
                                .withStringLiteral(doubleBackslashes(pattern.pattern()))
                                .on("Pattern")
                                .ofType(Pattern.class.getSimpleName());
                    });
                }
            }
        }

        private String doubleBackslashes(String s) {
            if (s.indexOf('\\') < 0) {
                return s;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '\\') {
                    sb.append(c);
                }
                sb.append(c);
            }
            return sb.toString();
        }

        @Override
        public void contributeDocComments(Consumer<String> bulletPoints) {
            if (pattern != null) {
                bulletPoints.accept("Must match the pattern <code>/" + doubleBackslashes(pattern.pattern()) + "/</code>");
            }
            if (minLength != 0) {
                bulletPoints.accept("Must be &gt;= " + minLength + " in length.");
            }
            if (maxLength != Integer.MAX_VALUE) {
                bulletPoints.accept("Must be &lt;= " + maxLength + " in length.");
            }
        }

        private <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X>
                ClassBuilder.ConditionBuilder<ClassBuilder.IfBuilder<B>>
                applyNullCheck(String fieldVariableName,
                        ClassBuilder.ConditionBuilder<ClassBuilder.IfBuilder<B>> iff) {
            if (nullable) {
//                return iff.isNotNull(fieldVariableName);
                return iff.parenthesize().booleanExpression(fieldVariableName
                        + " != null").closeParenthesesAnd().isTrue().and();
            }
            return iff;
        }

        @Override
        public <T, B extends ClassBuilder.BlockBuilderBase<T, B, X>, X> void generate(
                String fieldVariableName, String problemsListVariableName, String addMethodName, AnnotationUtils utils, B bb, String parameterName) {
            bb.lineComment(getClass().getName());
            if (minLength != 0) {
                applyNullCheck(fieldVariableName, bb.iff())
                        .invoke("length").on(fieldVariableName)
                        .isLessThan(minLength)
                        .invoke(addMethodName)
                        .withStringConcatentationArgument(parameterName)
                        .append(" must be at least ")
                        .append(minLength)
                        .append(" characters, but is ")
                        .appendExpression(fieldVariableName + ".length()")
                        .append(": '").appendExpression(fieldVariableName).append('\'')
                        .endConcatenation().on(problemsListVariableName).endIf();
            }
            if (maxLength != Integer.MAX_VALUE) {
                applyNullCheck(fieldVariableName, bb.iff())
                        .invoke("length").on(fieldVariableName)
                        .isGreaterThan(maxLength)
                        .invoke(addMethodName)
                        .withStringConcatentationArgument(parameterName)
                        .append(" must no longer than ")
                        .append(maxLength)
                        .append(" characters, but is ")
                        .appendExpression(fieldVariableName + ".length()")
                        .append(": '").appendExpression(fieldVariableName).append('\'')
                        .endConcatenation().on(problemsListVariableName).endIf();
            }
            if (pattern != null) {
                applyNullCheck(fieldVariableName, bb.iff())
                        .invoke("find")
                        .onInvocationOf("matcher")
                        .withArgument(fieldVariableName)
                        .on(patternVarName())
                        .eqaullingExpression("false")
                        //                        .endCondition()
                        .invoke(addMethodName)
                        .withStringConcatentationArgument("Value of ")
                        .append(parameterName)
                        .append(" '")
                        .appendExpression(fieldVariableName)
                        .append("' does not match the pattern /")
                        .append(doubleBackslashes(pattern.pattern()))
                        .append('/')
                        .append(": '").appendExpression(fieldVariableName).append('\'')
                        .endConcatenation()
                        .on(problemsListVariableName)
                        .endIf();
            }
        }
    }
}
