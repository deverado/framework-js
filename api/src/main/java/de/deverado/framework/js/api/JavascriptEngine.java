package de.deverado.framework.js.api;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

public interface JavascriptEngine extends AutoCloseable {

    Object runWithEngine();
}
