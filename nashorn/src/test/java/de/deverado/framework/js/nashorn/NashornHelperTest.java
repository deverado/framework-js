/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */
package de.deverado.framework.js.nashorn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import jdk.nashorn.api.scripting.NashornScriptEngine;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.junit.Ignore;
import org.junit.Test;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NashornHelperTest {

    @Test
    public void testCreateViaScriptManager() throws ScriptException, IOException {
        assertNotNull(NashornHelper.createEngine());
    }

    @Test
    public void testCreateWithAndWithoutOptTyping() throws ScriptException, IOException {
        assertNotNull(NashornHelper.createEngine(true));
        assertNotNull(NashornHelper.createEngine(false));
    }

    @Test
    public void testEvalIntoEngine() throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        NashornHelper.evalIntoEngine(engine, context, "globalFunc.js",
                "globalVar = 4;\n" +
                        "globalFuncVar = function(a,b) { return a + b; }\n" +
                        "function globalFunc(a,b) { return a + b; }\n");

        assertEquals(4, engine.eval("globalVar;", context));
        assertEquals(5l, engine.eval("globalFuncVar(3,2);", context));
        assertEquals(5l, engine.eval("globalFunc(3,2);", context));

        assertTrue(context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey("globalVar"));
        assertTrue(context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey("globalFuncVar"));
        assertTrue(context.getBindings(ScriptContext.ENGINE_SCOPE).containsKey("globalFunc"));

        assertFalse(engine.getBindings(ScriptContext.GLOBAL_SCOPE).containsKey("globalVar"));
        assertFalse(engine.getBindings(ScriptContext.GLOBAL_SCOPE).containsKey("globalFuncVar"));

        assertEquals(8.0, NashornHelper.eval(engine, context, "globalVar + globalVar"));
    }

    @Test
    public void testEval() throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        assertEquals(9, NashornHelper.eval(engine, context, " 5 + 4"));
    }

    @Test
    public void testInvokeFunction() throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        NashornHelper.evalIntoEngine(engine, context, "globalFunc.js",
                "globalFuncVar = function(a,b) { return a + b; }\n" +
                        "function globalFunc(a,b) { return a + b; }\n");

        assertEquals(5.0, NashornHelper.invokeFunction(engine, context, "globalFuncVar", 3, 2));
        assertEquals(5.0, NashornHelper.invokeFunction(engine, context, "globalFunc", 3, 2));
    }

    @Test
    public void testInvokeFunctionArrayParams() throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        NashornHelper.evalIntoEngine(engine, context, "globalFunc.js",
                "globalFuncVar = function(arr) { arr[2] = 'dings'; arr[arr.length-1].blub = 4; return arr[1]; }\n");

        Object[] inArr = {1, 2, 3, new HashMap()};
        Object result = NashornHelper.invokeFunction(engine, context, "globalFuncVar",
                new Object[]{inArr});
        assertEquals(2, result);
        assertEquals("dings", inArr[2]);
        assertEquals(2, result);
        assertEquals(4, ((Map) inArr[3]).get("blub"));

        NashornHelper.evalIntoEngine(engine, context, "globalFuncWithFrom.js",
                "globalFuncVarWithFrom = function(arr) { arr = Java.from(arr); arr[2] = 'dings'; return arr[1]; }\n");

        inArr = new Object[]{1, 2, 3};
        result = NashornHelper.invokeFunction(engine, context, "globalFuncVarWithFrom",
                new Object[]{inArr});
        assertEquals(2, result);
        assertEquals(3, inArr[2]);
    }

    @Test
    public void testInvokeMethod() throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        NashornHelper.evalIntoEngine(engine, context, "globalFunc.js",
                "globalVar = { objectProp: { base: 3 } };\n" +
                        "globalVar.objectProp.adder = function(b) { return this.base + b; }\n");

        Object objectFromProp = NashornHelper.getScriptObject(engine, context, "globalVar.objectProp");
        assertNotNull(objectFromProp);
        assertNotNull(NashornHelper.eval(engine, context, "globalVar.objectProp.adder"));
        assertEquals(8.0, NashornHelper.invokeMethod(engine, context, objectFromProp, "adder", 5));
    }

    @Test
    public void testCreateContext() {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);
        assertFalse(Objects.equals(context.getBindings(ScriptContext.ENGINE_SCOPE),
                context.getBindings(ScriptContext.GLOBAL_SCOPE)));
    }

    @Test
    public void testEnginePollutingGlobalScopeObjectsWithEval() throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        NashornHelper.evalIntoEngine(engine, context, "globalFunc.js",
                "globalObject = {}; \n");

        NashornHelper.eval(engine, context, "globalObject.newProp = 5; newLocalGlobal = 5;");

        // pollution:
        assertEquals(5, NashornHelper.eval(engine, context, "globalObject.newProp"));

        // ok, this got dropped:
        assertTrue((Boolean) NashornHelper.eval(engine, context, "typeof(newLocalGlobal) === 'undefined'"));
    }

    @Test
    public void testAvoidEnginePollutingGlobalScopeWithEvalLoad() throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        String code = "globalVar = 4;\n" +
                "globalFuncVar = function(a,b) { globalVar = a; newGlobalVar = b; }\n";
        Map<String, String> param = new HashMap<>();
        param.put("name", "globalFunc.js");
        param.put("script", code);
        context.getBindings(ScriptContext.ENGINE_SCOPE).put("loadParam", param);
        engine.eval("load(loadParam)", context);
        checkScopes(engine, context, true);
    }

    @Test
    public void testAvoidEnginePollutingGlobalScopeWithInvokeLoad() throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        String code = "globalVar = 4;\n" +
                "globalFuncVar = function(a,b) { globalVar = a; newGlobalVar = b; }\n";
        Map<String, String> param = new HashMap<>();
        param.put("name", "globalFunc.js");
        param.put("script", code);
        engine.setContext(context);
        engine.invokeFunction("load", param);

        checkScopes(engine, context, false);


    }

    private void checkScopes(NashornScriptEngine engine, ScriptContext context, boolean expectWeirdStuff) throws ScriptException {
        // NashornHelper keeps global stuff in ENGINE_SCOPE except when doing eval
        context.setBindings(context.getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.GLOBAL_SCOPE);
        // assign a throw-away scope for our test:
        context.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);

        engine.eval("var newLocal = 2; newLocalGlobal = 4; globalVar = 6;", context);

        assertEquals(2, context.getAttribute("newLocal", ScriptContext.ENGINE_SCOPE));
        assertEquals(4, context.getAttribute("newLocalGlobal", ScriptContext.ENGINE_SCOPE));
        assertEquals(6, context.getAttribute("globalVar", ScriptContext.ENGINE_SCOPE));

        // original value of globalVar shadowed
        assertEquals(4, context.getAttribute("globalVar", ScriptContext.GLOBAL_SCOPE));

        // WEIRD, stuff happening in engine shows up in GLOBAL_SCOPE, too!
        // This is due to the bindings being ScriptObjectMirrors and them behaving as while the engine is executing:
        // The Nashorn __noSuchElement__ (or sth like that) function is being called when the attribute is not
        // found in the local scope. And that in
        // turn queries the context for the value: Checks in which scope it is and returns it from the scope
        // it was found in! So the context gets associated with the bindings.
        if (expectWeirdStuff) {
            assertEquals(2, context.getAttribute("newLocal", ScriptContext.GLOBAL_SCOPE));
            assertEquals(4, context.getAttribute("newLocalGlobal", ScriptContext.GLOBAL_SCOPE));
        } else {
            assertNull(context.getAttribute("newLocal", ScriptContext.GLOBAL_SCOPE));
            assertNull(context.getAttribute("newLocalGlobal", ScriptContext.GLOBAL_SCOPE));
        }

        // try erasing the vars - :
        Bindings erasedEngineBindings = context.getBindings(ScriptContext.ENGINE_SCOPE);
        context.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        assertNull(context.getAttribute("newLocal", ScriptContext.GLOBAL_SCOPE));
        assertNull(context.getAttribute("newLocalGlobal", ScriptContext.GLOBAL_SCOPE));

        // successful. So: Important to delete the association of the engine scope bindings from the
        // context - OTHERWISE the global is returning stuff from the ENGINE_SCOPE bindings.

        // They still live in the old engine bindings:
        assertEquals(2, erasedEngineBindings.get("newLocal"));
        assertEquals(4, erasedEngineBindings.get("newLocalGlobal"));

        engine.eval("globalFuncVar( 3, 5);", context);
        // stuff living in global scope creates new variables in global scope and changes global scope:
        assertEquals(3, context.getAttribute("globalVar", ScriptContext.GLOBAL_SCOPE));
        assertEquals(5, context.getAttribute("newGlobalVar", ScriptContext.GLOBAL_SCOPE));

        // WEIRD, stuff happening in global shows up in ENGINE_SCOPE, too! See above for explanation. But also:
        // This only happens when load was called with eval and after a globalFunc was executed.
        assertFalse(Objects.equals(context.getBindings(ScriptContext.ENGINE_SCOPE),
                context.getBindings(ScriptContext.GLOBAL_SCOPE)));
        Object globalVarInEngineScope = context.getBindings(ScriptContext.ENGINE_SCOPE).get("globalVar");

        assertEquals(3, globalVarInEngineScope);
        assertEquals(5, context.getAttribute("newGlobalVar", ScriptContext.ENGINE_SCOPE));

        // delete local assignments and try again:
        context.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);

        assertEquals(3, context.getAttribute("globalVar", ScriptContext.GLOBAL_SCOPE));
        assertEquals(5, context.getAttribute("newGlobalVar", ScriptContext.GLOBAL_SCOPE));

        // WEIRD, stuff happening in global shows up in ENGINE_SCOPE, too! See above for explanation
        assertFalse(Objects.equals(context.getBindings(ScriptContext.ENGINE_SCOPE),
                context.getBindings(ScriptContext.GLOBAL_SCOPE)));
        globalVarInEngineScope = context.getBindings(ScriptContext.ENGINE_SCOPE).get("globalVar");

        assertNull(globalVarInEngineScope);
        assertNull(context.getAttribute("newGlobalVar", ScriptContext.ENGINE_SCOPE));
    }

    @Test
    public void testLoadWithRequire() throws Exception {
        testLoadWithRequire("de/deverado/framework/js/nashorn/require.js");
    }

    @Test
    public void testLoadWithRequireFake() throws Exception {
        testLoadWithRequire("de/deverado/framework/js/nashorn/requireFake.js");
    }

    public void testLoadWithRequire(String whichRequire) throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        LinkedHashMap<String, CharSource> oneTarget = new LinkedHashMap<>();
        NashornHelper.addClasspathResources(oneTarget, "de/deverado/framework/js/nashorn/uglify",
                "./lib/parse-js.js");

        NashornHelper.loadWithRequire(engine, context, oneTarget, whichRequire);

        String uglifyScript = "function test() {\n" +
                "var result = {};\n" +
                "result.first = require('lib/parse-js.js');\n" +
                "result.second = result.first.tokenizer;\n" +
                "return result; \n" +
                "}\n";

        NashornHelper.evalIntoEngine(engine, context, "uglifyCaller", uglifyScript);

        ScriptObjectMirror result = (ScriptObjectMirror) NashornHelper.invokeFunction(engine, context, "test");
        assertNotNull(result.get("first"));
        assertNotNull(result.get("second"));
    }

    @Test
    public void testComplexLoadWithRequire() throws Exception {
        testComplexLoadWithRequire("de/deverado/framework/js/nashorn/require.js");
    }

    @Test
    public void testComplexLoadWithRequireFake() throws Exception {
        testComplexLoadWithRequire("de/deverado/framework/js/nashorn/requireFake.js");
    }

    public void testComplexLoadWithRequire(String whichRequire) throws Exception {
        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        LinkedHashMap<String, CharSource> uglifyTargets = new LinkedHashMap<>();
        NashornHelper.addClasspathResources(uglifyTargets, "de/deverado/framework/js/nashorn/uglify",
                "./lib/parse-js.js", "./lib/consolidator.js", "./lib/process.js", "./lib/squeeze-more.js",
                "uglify.js");

        NashornHelper.loadWithRequire(engine, context, uglifyTargets, whichRequire);

        String uglifyScript = "function uglify(orig_code) {\n" +
                "var jsp = require('uglify').parser;\n" +
                "var pro = require('uglify').uglify;\n" +
                "\n" +
                "var ast = jsp.parse(orig_code); // parse code and get the initial AST\n" +
                "ast = pro.ast_mangle(ast); // get a new AST with mangled names\n" +
                "ast = pro.ast_squeeze(ast); // get an AST with compression optimizations\n" +
                "return pro.gen_code(ast); // compressed code here\n" +
                "}\n"
                //+"require('/uglify.js');"
                ;

        NashornHelper.evalIntoEngine(engine, context, "uglifyCaller", uglifyScript);

        String origCode = "function adder(param) { if (param) { return param + param; } else { return 0; } }";

        assertEquals("function adder(e){return e?e+e:0}",
                NashornHelper.invokeFunction(engine, context, "uglify", origCode));

        // try another load:
        LinkedHashMap<String, CharSource> otherSources = new LinkedHashMap<>();
        otherSources.put("someMod.js", CharSource.wrap("exports.func = function() { return 'hallo!'; };"));
        NashornHelper.loadWithRequire(engine, context, otherSources);

        assertEquals("hallo!", NashornHelper.eval(engine, context, "require('someMod').func();"));
        // should still live in there
        assertTrue((Boolean) NashornHelper.eval(engine, context, "require('uglify') != null"));
    }

    @Ignore
    @Test
    public void testHighPerformanceIntoEngineFuncCall() throws Exception {
        String globalCode = "globalFuncVar = function(a,b) { return a + b; }\n" +
                "globalObj = { func: function(a,b) { return a + b; } }\n" +
                "function globalFunc(a,b) { return a + b; }\n";

        NashornScriptEngine engine = NashornHelper.createEngine();
        ScriptContext context = NashornHelper.createContext(engine);

        NashornHelper.evalIntoEngine(engine, context, "globalFunc.js",
                globalCode);

        // possibility of using compiledScript:
        //CompiledScript compiled = engine.compile("globalFuncVar.apply(globalFuncVar, args);");
        //context.setAttribute("args", Arrays.asList(3, 5), ScriptContext.ENGINE_SCOPE);


        //CompiledScript compiled = engine.compile("globalFuncVar( 3, 5)");
        //CompiledScript compiledArgs = engine.compile("globalFuncVar( a, b)");
        //ScriptContext fakeContext = new SimpleScriptContext();

        engine.setContext(context);

        //NashornJavascriptEngineContext c = NashornJavascriptEngineContext.createWithNewEngine();
        //c.evalIntoEngine("globalCode", globalCode);

        for (int o = 0; o < 200; o++) {
            Long expected = 8l;
            // this createBindings call adds 100 % to the runtime of the 100000 invokeFunction calls
            //Bindings args = engine.createBindings();
            //HashSet<String> possibleKeys = new HashSet<String>(Arrays.asList("a", "b"));
            //Object globalObject = engine.eval("globalObj");

            System.gc();
            Stopwatch timer = Stopwatch.createStarted();
            for (int i = 0; i<100000; i++) {
                //used.compareAndSet(false, true); - this takes nothing to 1ms
                // 15 ms - globalFuncVar and globalFunc don't differ by a lot
                assertEquals(8.0, engine.invokeFunction("globalFuncVar", 3, 5));
                //used.set(false);
                //engine.setContext(fakeContext); // switching contexts slows by 100%
                // 500 ms
                //assertEquals(expected, engine.eval("globalFuncVar( 3, 5)"));
                // 17 ms
                //assertEquals(expected, compiled.eval());

                // 15 ms
                //assertEquals(8.0, engine.invokeMethod(globalObject, "func", 3, 5));

                // 60 secs if args created in loop
                //Bindings args = engine.createBindings();
                // 2.2 secs if args created outside of loop
                //args.put("a", 3);
                //args.put("b", 5);
                //assertEquals(8.0, compiledArgs.eval(args));
                // cleaning args with this results in even lower execution speed (3.3 secs):
                //for (String k : args.keySet()) {
                //    args.remove(k);
                //    assertTrue(k, possibleKeys.contains(k));
                //}
            }
            System.out.println("" + timer);
        }

    }

    @Ignore
    @Test
    public void engineCreationSpeedTest() throws Exception {

        double nanoToSecondFactor = 0.000000001;
        CPUTimeStopwatch timer = CPUTimeStopwatch.createStarted();
        LinkedHashMap<String, CharSource> uglifyTargets = new LinkedHashMap<>();
        NashornHelper.addClasspathResources(uglifyTargets, "vcode/framework/nashorn/uglify",
                "./lib/parse-js.js", "./lib/consolidator.js", "./lib/process.js", "./lib/squeeze-more.js",
                "uglify.js");

        String contentToUglify = loadUglifySrcPart();

        System.setProperty("nashorn.typeInfo.maxFiles", "20000");

        //NashornScriptEngine engine = NashornHelper.createEngine(true);
        //timer.restart();
        //NashornHelper.requireWrap(engine, uglifyTargets);
        //System.out.format("Uglify took: %s\n", timer.format("%.3f", nanoToSecondFactor));

        NashornScriptEngine nse2 = null;
        //nse2 = NashornHelper.createEngine(false);
        //NashornHelper.requireWrap(nse2, uglifyTargets);

        List<NashornScriptEngine> engines = new ArrayList<>();

        NashornScriptEngine engine = NashornHelper.createEngine();
        SimpleScriptContext ctxt = new SimpleScriptContext();
        ctxt.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
        NashornHelper.loadWithRequire(engine, ctxt, uglifyTargets);

        engines.add(engine);
        //engine = NashornHelper.createEngine();
        //NashornHelper.requireWrap(engine, uglifyTargets);
        //engines.add(engine);
        for (int i = 0; i < 200; i++) {

            //engine = engines.get(i % 2);

            //timer.restart();
            //DustNashorn.loadIntoEngine(engine);
            //System.out.format("Dust took: %s\n", timer.format("%.3f", nanoToSecondFactor));

            //engine.eval("delete require; delete exports; delete module;");
            //timer.restart();
            //NashornHelper.requireWrap(engine, uglifyTargets);
            //System.out.format("Uglify took: %s\n", timer.format("%.3f", nanoToSecondFactor));

            if (i > 2) {
                if (engine != nse2) {
                    //nse2 = NashornHelper.createEngine();
                    //NashornHelper.requireWrap(nse2, uglifyTargets);

                    //engine = nse2;
                    ctxt.setBindings(ctxt.getBindings(ScriptContext.ENGINE_SCOPE), ScriptContext.GLOBAL_SCOPE);
                    nse2 = engine;
                    ctxt.setBindings(engine.createBindings(), ScriptContext.ENGINE_SCOPE);
                }

                timer.restart();
                uglify(engine, contentToUglify);
                System.out.format("new; %d;;; %s\n", i + 1,
                        formatCsv(timer));
                if (ctxt != engine.getContext()) {
                    throw new Exception("Context changed??");
                }
            } else {
                timer.restart();
                uglify(engine, contentToUglify);
                System.out.format("first; %d; %s\n", i + 1,
                        formatCsv(timer));
            }

        }

        //NashornScriptEngine engine = NashornHelper.createEngine();
        //
        //timer.restart();
        //DustNashorn.loadIntoEngine(engine);
        //
        //System.out.format("Dust took: %s\n", timer.format("%.3f", nanoToSecondFactor));

        //timer.restart();
        //NashornHelper.requireWrap(engine, requireTargets);
        //System.out.format("Uglify took: %s\n", timer.format("%.3f", nanoToSecondFactor));


    }

    private CharSequence formatCsv(CPUTimeStopwatch timer) {
        double nanoToSecondFactor = 0.000000001;
        return String.format("%.3f; %.3f",
                timer.elapsedReal() * nanoToSecondFactor, timer.elapsed() * nanoToSecondFactor);
    }

    public String uglify(NashornScriptEngine engine, String content)
            throws ScriptException, NoSuchMethodException {

        String uglifyScript = "function uglify(orig_code) {\n" +
                "var jsp = require(\"uglify\").parser;\n" +
                "var pro = require(\"uglify\").uglify;\n" +
                "\n" +
                "var ast = jsp.parse(orig_code); // parse code and get the initial AST\n" +
                "ast = pro.ast_mangle(ast); // get a new AST with mangled names\n" +
                "ast = pro.ast_squeeze(ast); // get an AST with compression optimizations\n" +
                "return pro.gen_code(ast); // compressed code here\n" +
                "}";
        if (engine.get("uglify") == null) {
            engine.eval(uglifyScript);
        }
        //engine.put("orig_code", content);
        //String result = (String) engine.eval("uglify(orig_code);"); // invokeFunction doesn't work here
        //engine.getBindings(ScriptContext.ENGINE_SCOPE).remove("orig_code");
        String result = (String) engine.invokeFunction("uglify", content);
        return result;
    }

    private String loadUglifySrcPart() throws IOException {
        StringBuilder builder = new StringBuilder();

        String root = "vcode/framework/nashorn/uglify";
        for (String s : new String[]{
                //"./lib/parse-js.js",
                "./lib/consolidator.js",
                // "./lib/process.js", "./lib/squeeze-more.js",
                //"uglify.js"
        }) {
            builder.append(Resources.toString(Resources.getResource(root + "/" + s), Charsets.UTF_8));
        }
        return builder.toString();
    }
}
