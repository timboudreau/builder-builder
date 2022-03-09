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
import com.mastfrog.builder.annotation.processors.BuilderDescriptors.BuilderDescriptor;
import com.mastfrog.builder.annotation.processors.spi.ConstraintGenerator;
import com.mastfrog.util.service.ServiceProvider;
import com.mastfrog.util.strings.Strings;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 *
 * @author Tim Boudreau
 */
@SupportedAnnotationTypes(BuilderAnnotationProcessor.ANNO)
@ServiceProvider(Processor.class)
public class BuilderAnnotationProcessor extends AbstractProcessor {

    private static final int CURRENT_CODE_GENERATION_VERSION = 1;
    private static final int MIN_CODE_GENERATION_VERSION = 1;
    private AnnotationUtils utils;
    private BuilderDescriptors descs;
    private ConstraintHandlers handlers;
    static final String ANNO = "com.mastfrog.builder.annotations.GenerateBuilder";
    static final String NULLABLE = "com.mastfrog.builder.annotations.Nullable";

    static {
        AnnotationUtils.forceLogging();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        utils = new AnnotationUtils(processingEnv, Collections.singleton(ANNO), BuilderAnnotationProcessor.class);
        this.descs = new BuilderDescriptors(utils);
        this.handlers = new ConstraintHandlers(utils);
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment re) {
        Set<Element> annotateds = utils.findAnnotatedElements(re);
        for (Iterator<Element> it = annotateds.iterator(); it.hasNext();) {
            if (!validate(it.next())) {
                it.remove();
            }
        }
        for (Element e : annotateds) {
            AnnotationMirror mir = utils.findMirror(e, ANNO);
            record(e, mir);
        }
        boolean result = annotateds.isEmpty();
        if (re.processingOver()) {
            try {
                descs.generate();
            } catch (IOException ex) {
                utils.fail(Strings.toString(ex));
            }
        }
        return result;
    }

    void record(Element el, AnnotationMirror mir) {
        Set<BuilderStyles> styles = BuilderStyles.styles(utils, mir);
        String builderNameFromAnnotation = utils.annotationValue(mir, "className", String.class, null);
        int codeGenerationVersion = utils.annotationValue(mir, "codeGenerationVersion", Integer.class, CURRENT_CODE_GENERATION_VERSION);
        if (codeGenerationVersion > CURRENT_CODE_GENERATION_VERSION) {
            utils.fail("Unknown code-generation-version " + codeGenerationVersion + " - possibly "
                    + "you have an older version of this library ahead of the one you want to use "
                    + "on your classpath", el, mir);
            return;
        }
        if (codeGenerationVersion < MIN_CODE_GENERATION_VERSION) {
            utils.fail("Code generation version " + codeGenerationVersion + " is not supported "
                    + "by this version of BuilderAnnotationProcessor.  Perhaps you need an "
                    + "older version of it?", el, mir);
            return;
        }
        if (builderNameFromAnnotation != null) {
            String pkg = utils.packageName(el);
            TypeMirror conflict = utils.type(pkg + "." + builderNameFromAnnotation);
            if (conflict != null) {
                utils.fail("Cannot name a builder '" + pkg + "." + builderNameFromAnnotation + " - "
                    + "a class named " + builderNameFromAnnotation + " already exists in that package");
                return;
            }
        }
        descs.add(el, styles, builderNameFromAnnotation, codeGenerationVersion, desc -> {
            if (el.getKind() == ElementKind.METHOD) {
                desc.onInstanceOf(AnnotationUtils.enclosingType(el));
            }
            ExecutableElement ex = (ExecutableElement) el;
            String name = ex.getSimpleName().toString();
            for (VariableElement param : ex.getParameters()) {
                AnnotationMirror nullableMirror = utils.findAnnotationMirror(param, NULLABLE);
                String fieldName = param.getSimpleName().toString();
                handleOneParameter(desc, el, fieldName, nullableMirror != null, param);
            }
        });
    }

    void handleOneParameter(BuilderDescriptor bd, Element target, String fieldName, boolean optional, VariableElement on) {
        Set<ConstraintGenerator> gen = handlers.generators(target, on);
        bd.handleOneParameter(fieldName, optional, on, gen);
    }

    private boolean validate(Element el) {
        ElementKind kind = el.getKind();
        if (kind == null) {
            utils.fail("No element kind (broken source?)", el);
            return false;
        }
        boolean result = false;
        switch (kind) {
            case CONSTRUCTOR:
            case METHOD:
                result = true;
                break;
            default:
                utils.fail("Cannot annotate a " + kind + " with " + ANNO);
                return false;
        }
        ExecutableElement ex = (ExecutableElement) el;
        if (ex.getParameters().isEmpty()) {
            utils.fail("Cannot create a builder for a constructor or method with no parameters.", el);
            result = false;
        }
        return result;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
