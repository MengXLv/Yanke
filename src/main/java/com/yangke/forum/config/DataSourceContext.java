package com.yangke.forum.config;

/**
 * 数据源上下文持有者（ThreadLocal）
 * 面试要点：ThreadLocal 线程隔离，每个请求独立选择数据源
 */
public class DataSourceContext {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    public static void setMaster() { CONTEXT.set("master"); }
    public static void setSlave() { CONTEXT.set("slave"); }
    public static String get() { return CONTEXT.get() != null ? CONTEXT.get() : "master"; }
    public static void clear() { CONTEXT.remove(); }
}
