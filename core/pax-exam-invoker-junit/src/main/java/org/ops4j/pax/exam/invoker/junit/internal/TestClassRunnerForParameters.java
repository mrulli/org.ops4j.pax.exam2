/*
 * Copyright 2013 Harald Wellmann
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.ops4j.pax.exam.invoker.junit.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;

import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.model.FrameworkField;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.ops4j.pax.exam.util.Injector;

/**
 * Runner for parameterized test classes.
 *
 * Based on {@code org.junit.runners.Parameterized}.
 * @author Harald Wellmann
 *
 */
class TestClassRunnerForParameters extends BlockJUnit4ClassRunner {

    private final Object[] fParameters;

    private final String fName;

    private Injector injector;

    TestClassRunnerForParameters(Class<?> type, Object[] parameters, String name, Injector injector)
        throws InitializationError {
        super(type);
        fParameters = parameters;
        fName = name;
        this.injector = injector;
    }

    @Override
    public Object createTest() throws Exception {
        Object test = null;
        if (fieldsAreAnnotated()) {
            test = createTestUsingFieldInjection();
        }
        else {
            test = createTestUsingConstructorInjection();
        }
        injector.injectFields(test);
        return test;
    }

    private Object createTestUsingConstructorInjection() throws Exception {
        return getTestClass().getOnlyConstructor().newInstance(fParameters);
    }

    private Object createTestUsingFieldInjection() throws Exception {
        List<FrameworkField> annotatedFieldsByParameter = getAnnotatedFieldsByParameter();
        if (annotatedFieldsByParameter.size() != fParameters.length) {
            throw new Exception("Wrong number of parameters and @Parameter fields."
                + " @Parameter fields counted: " + annotatedFieldsByParameter.size()
                + ", available parameters: " + fParameters.length + ".");
        }
        Object testClassInstance = getTestClass().getJavaClass().newInstance();
        for (FrameworkField each : annotatedFieldsByParameter) {
            Field field = each.getField();
            Parameter annotation = field.getAnnotation(Parameter.class);
            int index = annotation.value();
            try {
                field.set(testClassInstance, fParameters[index]);
            }
            catch (IllegalArgumentException iare) {
                throw new Exception(getTestClass().getName() + ": Trying to set " + field.getName()
                    + " with the value " + fParameters[index] + " that is not the right type ("
                    + fParameters[index].getClass().getSimpleName() + " instead of "
                    + field.getType().getSimpleName() + ").", iare);
            }
        }
        return testClassInstance;
    }

    @Override
    protected String getName() {
        return fName;
    }

    @Override
    protected String testName(FrameworkMethod method) {
        return method.getName() + getName();
    }

    @Override
    protected void validateConstructor(List<Throwable> errors) {
        validateOnlyOneConstructor(errors);
        if (fieldsAreAnnotated()) {
            validateZeroArgConstructor(errors);
        }
    }

    @Override
    protected void validateFields(List<Throwable> errors) {
        super.validateFields(errors);
        if (fieldsAreAnnotated()) {
            List<FrameworkField> annotatedFieldsByParameter = getAnnotatedFieldsByParameter();
            int[] usedIndices = new int[annotatedFieldsByParameter.size()];
            for (FrameworkField each : annotatedFieldsByParameter) {
                int index = each.getField().getAnnotation(Parameter.class).value();
                if (index < 0 || index > annotatedFieldsByParameter.size() - 1) {
                    errors.add(new Exception("Invalid @Parameter value: " + index
                        + ". @Parameter fields counted: " + annotatedFieldsByParameter.size()
                        + ". Please use an index between 0 and "
                        + (annotatedFieldsByParameter.size() - 1) + "."));
                }
                else {
                    usedIndices[index]++;
                }
            }
            for (int index = 0; index < usedIndices.length; index++) {
                int numberOfUse = usedIndices[index];
                if (numberOfUse == 0) {
                    errors.add(new Exception("@Parameter(" + index + ") is never used."));
                }
                else if (numberOfUse > 1) {
                    errors.add(new Exception("@Parameter(" + index + ") is used more than once ("
                        + numberOfUse + ")."));
                }
            }
        }
    }

    @Override
    protected Statement classBlock(RunNotifier notifier) {
        return childrenInvoker(notifier);
    }

    @Override
    protected Annotation[] getRunnerAnnotations() {
        return new Annotation[0];
    }

    private List<FrameworkField> getAnnotatedFieldsByParameter() {
        return getTestClass().getAnnotatedFields(Parameter.class);
    }

    private boolean fieldsAreAnnotated() {
        return !getAnnotatedFieldsByParameter().isEmpty();
    }
}
