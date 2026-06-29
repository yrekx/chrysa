package com.android.chrysaoralike;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.xmlpull.v1.XmlSerializer;

public class GetDeviceInformation {

    public static void collectingData(Context context, XmlSerializer xmlSerializer, StringBuilder str){
        String networkInUsed;
        ConnectivityManager connMng = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] allNetworkIn4 = connMng.getAllNetworkInfo();
        int nwifLen = allNetworkIn4.length;
        int i = 0;
        while (true){
            if(i<nwifLen){
                if(allNetworkIn4[i].getTypeName().equalsIgnoreCase("WIFI") && allNetworkIn4[i].isConnected()){
                    networkInUsed = "wifi";
                } else if (allNetworkIn4[i].getTypeName().equalsIgnoreCase("MOBILE") && allNetworkIn4[i].isConnected()){
                    networkInUsed = "roaming state";
                } else {
                    i++;
                }
                

            }
        }
    }
}
