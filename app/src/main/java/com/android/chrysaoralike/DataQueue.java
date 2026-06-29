package com.android.chrysaoralike;

import java.util.concurrent.ConcurrentLinkedQueue;

public class DataQueue {
    private static DataQueue instance;
    private ConcurrentLinkedQueue<DataPart> queue = new ConcurrentLinkedQueue<>();
    private DataQueue() {}
    public static synchronized DataQueue getInstance() { if (instance == null) instance = new DataQueue(); return instance; }
    public void addPart(String name, byte[] content, boolean isJpg) { queue.offer(new DataPart(name, content, isJpg)); }
    public DataPart poll() { return queue.poll(); }
    public boolean isEmpty() { return queue.isEmpty(); }
    public static class DataPart { public String name; public byte[] content; public boolean isJpg; public DataPart(String name, byte[] content, boolean isJpg) { this.name = name; this.content = content; this.isJpg = isJpg; } }
}