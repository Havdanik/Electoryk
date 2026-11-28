package com.elektoryk.scan.printing;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import java.io.OutputStream;
import java.util.UUID;

public final class BluetoothThermalPrinter {
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothThermalPrinter(){}

    public static void print(String mac,byte[] data)throws Exception{
        if(mac==null||mac.trim().isEmpty())throw new IllegalArgumentException("Bluetooth MAC не задано");
        if(data==null||data.length==0)throw new IllegalArgumentException("Порожнє завдання друку");
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if(adapter==null)throw new IllegalStateException("Bluetooth недоступний");
        if(!adapter.isEnabled())throw new IllegalStateException("Bluetooth вимкнений");
        BluetoothDevice device=adapter.getRemoteDevice(mac);

        Exception first=null;
        try{
            printSocket(adapter,device.createRfcommSocketToServiceRecord(SPP),data);
            return;
        }catch(Exception e){first=e;}
        try{
            printSocket(adapter,device.createInsecureRfcommSocketToServiceRecord(SPP),data);
        }catch(Exception second){
            if(first!=null)second.addSuppressed(first);
            throw second;
        }
    }

    private static void printSocket(BluetoothAdapter adapter,BluetoothSocket socket,byte[] data)throws Exception{
        try{
            adapter.cancelDiscovery();
            socket.connect();
            OutputStream out=socket.getOutputStream();
            int offset=0;
            final int chunk=1024;
            while(offset<data.length){
                int n=Math.min(chunk,data.length-offset);
                out.write(data,offset,n);
                offset+=n;
                if(offset<data.length)Thread.sleep(12);
            }
            out.flush();
            Thread.sleep(180);
        }finally{
            try{socket.close();}catch(Exception ignored){}
        }
    }
}
