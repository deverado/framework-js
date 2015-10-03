/*
 * Copyright (c) Georg Koester 2012-15. All rights reserved.
 */
package de.deverado.framework.js.guice;

import static org.junit.Assert.assertEquals;

import com.google.inject.Guice;
import de.deverado.framework.guice.coreext.problemreporting.LoggingProblemReporterModule;
import de.deverado.framework.js.api.JavascriptEngineContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.function.Function;

public class JavascriptExecutorModuleTest {

    private JavascriptExecutor cut;

    @Before
    public void setUp() throws Exception {
        cut = Guice.createInjector(new LoggingProblemReporterModule(),
                new JavascriptExecutorModule(), new NashornJavascriptModule())
                .getInstance(JavascriptExecutor.class);
    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testSubmitEval() throws Exception {
        assertEquals(5, cut.submitEval("2+3").get());
    }

    @Test
    public void testSubmitMethodInvocation() throws Exception {
        Object gobj = cut.submit(new Function<JavascriptEngineContext, Object>() {
            @Override
            public Object apply(JavascriptEngineContext javascriptEngineContext) {
                javascriptEngineContext.evalIntoEngine("globalCode",
                        "gobj = {a: 3, adder: function (b) { return this.a+b;} }");
                return javascriptEngineContext.getScriptObject("gobj");
            }
        }).get();
        assertEquals(5.0, cut.submitMethodInvocation(gobj, "adder", 2).get());
    }
}
