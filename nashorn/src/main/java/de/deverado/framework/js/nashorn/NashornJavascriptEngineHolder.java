package de.deverado.framework.js.nashorn;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import jdk.nashorn.api.scripting.NashornScriptEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.ConcurrentModificationException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Does locking to check for (accidental) concurrent access.
 */
public class NashornJavascriptEngineHolder {

    private static final Logger LOG = LoggerFactory.getLogger(NashornJavascriptEngineHolder.class);
    private NashornScriptEngine engine;

    private ReentrantLock simpleLock = new ReentrantLock();

    boolean lockingEnabled = true;

    public static NashornJavascriptEngineHolder createWithNewEngine() {
        NashornJavascriptEngineHolder result = new NashornJavascriptEngineHolder();
        result.engine = NashornHelper.createEngine();
        return result;
    }

    public static NashornJavascriptEngineHolder createForExistingEngine(NashornScriptEngine engine) {
        NashornJavascriptEngineHolder result = new NashornJavascriptEngineHolder();
        result.engine = engine;
        return result;
    }

    /**
     * If you are sure about what you do you can disable locking and improve call overhead by sth like 20%, but do
     * this only if you know what you're doing. Don't change this value after initialization!
     * @param lockingEnabled set to false to stop the internal checking with a reentrant lock
     */
    public void setLockingEnabled(boolean lockingEnabled) {
        // synchronize to force some cache sync
        synchronized (this) {
            this.lockingEnabled = lockingEnabled;
        }
    }

    /**
     * Locks the engine and executes function.
     * @param function to exec
     * @return result of function
     * @throws IllegalStateException if engine is in use.
     */
    public Object withEngine(Function<NashornScriptEngine, Object> function) {
        boolean acquired = tryLock();
        if (!acquired) {
            throw new IllegalStateException("Concurrent access disallowed");
        }
        try {
            return function.apply(engine);
        } finally {
            releaseLock();
        }
    }

    private boolean tryLock() {
        if (!lockingEnabled) {
            return true;
        }
        return simpleLock.tryLock();
    }

    public NashornJavascriptEngine open() throws IllegalStateException {
        boolean acquired = tryLock();
        if (!acquired) {
            throw new IllegalStateException("Concurrent access disallowed");
        }
        return new NashornJavascriptEngine(engine);
    }

    @Nullable
    protected NashornJavascriptEngine tryOpen() {
        boolean acquired = tryLock();
        if (!acquired) {
            return null;
        }
        return new NashornJavascriptEngine(NashornJavascriptEngineHolder.this.engine);
    }

    private void releaseLock() {
        if (!lockingEnabled) {
            return;
        }
        try {
            simpleLock.unlock();
        } catch (IllegalMonitorStateException imse) {
            throw new ConcurrentModificationException("Lock was released elsewhere!", imse);
        }
    }

    public class NashornJavascriptEngine implements AutoCloseable {

        NashornScriptEngine engine;

        private NashornJavascriptEngine(NashornScriptEngine engine) {
            this.engine = engine;
        }

        public NashornScriptEngine getEngine() {
            return engine;
        }

        @Override
        public void close() throws Exception {
            if (engine != null) {
                engine = null;
                releaseLock();
            }
        }
    }

}
