package com.android.chrysaoralike;

import java.util.concurrent.ConcurrentLinkedQueue;

public class CommandQueue {
    private static CommandQueue instance;
    private ConcurrentLinkedQueue<SmsCommand> queue = new ConcurrentLinkedQueue<>();
    private CommandQueue() {}
    public static synchronized CommandQueue getInstance() { if (instance == null) instance = new CommandQueue(); return instance; }
    public void enqueue(SmsCommand cmd) { queue.offer(cmd); }
    public SmsCommand poll() { return queue.poll(); }
    public boolean isEmpty() { return queue.isEmpty(); }
}