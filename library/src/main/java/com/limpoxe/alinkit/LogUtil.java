package com.limpoxe.alinkit;

import com.alibaba.fastjson.JSON;

public class LogUtil {
    private static Logger sLogger = null;

    public static void setLogger(Logger logger) {
        sLogger = logger;
    }

    public static void log(String tag, String msg) {
        if (sLogger != null) {
            sLogger.log("[" + Thread.currentThread().getName() + "][" + tag +"]" + msg);
        }
    }

    public static void log(String msg) {
        if (sLogger != null) {
            sLogger.log("[" + Thread.currentThread().getName() + "]" + msg);
        }
    }

    public interface Logger {
        public void log(String msg);
    }

    public static String safeToString(Object... args) {
        if (args == null || args.length <= 0) {
            return "";
        }
        try {
            return JSON.toJSONString(args);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (Object a : args) {
                if (a != null) {
                    sb.append(" ").append(a.toString()).append(" ");
                } else {
                    sb.append(" null ");
                }
            }
            return sb.toString();
        }
    }
}
