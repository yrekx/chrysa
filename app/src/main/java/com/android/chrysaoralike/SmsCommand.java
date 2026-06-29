package com.android.chrysaoralike;

public class SmsCommand {
    public int commandId, ackId;
    public String args, signature, rawMessage;
    public SmsCommand(int commandId, int ackId, String args, String signature, String rawMessage) {
        this.commandId = commandId; this.ackId = ackId; this.args = args;
        this.signature = signature; this.rawMessage = rawMessage;
    }
    @Override public String toString() {
        return "SmsCommand{cmd=" + commandId + ", ack=" + ackId + ", args='" + args + "'}";
    }
}