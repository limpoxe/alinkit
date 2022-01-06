package com.limpoxe.alinkit;

public class LogUtil {
    private static Logger sLogger = null;

    public static void log(String msg) {
        if (sLogger != null) {
            sLogger.log("[" + Thread.currentThread().getName() + "]" + msg);
        }
    }

    public static void setLogger(Logger logger) {
        sLogger = logger;
    }

    public interface Logger {
        public void log(String msg);
    }
}
