package de.deverado.framework.js.nashorn;/*
 * Copyright Georg Koester 2012-15. All rights reserved.
 */

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

/**
 * Copied here, might end up in a framework-rogue package sometime.
 */
public class CPUTimeStopwatch {

    private long startCPU;
    private long startReal;
    private final com.sun.management.OperatingSystemMXBean sunBean;

    public CPUTimeStopwatch() {
        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        if (operatingSystemMXBean instanceof com.sun.management.OperatingSystemMXBean) {
            sunBean = (com.sun.management.OperatingSystemMXBean)
                    operatingSystemMXBean;
        } else {
            throw new IllegalStateException("Not running on sun VM, not possible to measure CPU time");
        }
    }

    public static CPUTimeStopwatch createStarted() {
        CPUTimeStopwatch result = new CPUTimeStopwatch();

        result.restart();
        return result;
    }

    public void restart() {
        startCPU = sunBean.getProcessCpuTime();
        startReal = System.nanoTime();
    }

    public long elapsed() {
        return sunBean.getProcessCpuTime() - startCPU;
    }

    public long elapsedReal() {
        return System.nanoTime() - startReal;
    }

    @Override
    public String toString() {
        return format("%.0f", 1);
    }

    public String format(String format, double factor) {
        double cpu = elapsed() * factor;
        double real = elapsedReal() * factor;
        return String.format("{real=" + format + ",cpu=" + format + "}", real, cpu);
    }
}
