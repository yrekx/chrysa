package com.android.chrysaoralike;

public class ConfigData {
    public String token;
    public String command;
    public String mcc;
    public String installFlag;
    public String host;
    public String port;

    public ConfigData(String token, String command, String mcc, String installFlag) {
        this.token = token;
        this.command = command;
        this.mcc = mcc;
        this.installFlag = installFlag;
    }

    public boolean isValid() {
        return token != null && !token.isEmpty();
    }
}