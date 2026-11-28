package com.elektoryk.scan.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.elektoryk.scan.model.CommercialPlan;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.printing.BluetoothThermalPrinter;
import com.elektoryk.scan.printing.EstimateReceiptBuilder;
import com.elektoryk.scan.storage.CommercialRepository;
import com.elektoryk.scan.storage.FileProjectRepository;

import java.util.ArrayList;

public class EstimateThermalActivity extends Activity{
    private static final int REQ=78;
    private String projectId;
    private ChoiceStrip devices;
    private TextView status;
    private boolean bluetoothPermissionDenied;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);projectId=getIntent().getStringExtra("projectId");
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ);else build();
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g);
        if(r==REQ){bluetoothPermissionDenied=!(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED);build();}
    }
    private void build(){
        ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Чек кошторису","Швидкий друк клієнту на Bluetooth ESC/POS.","RECEIPT",true));
        devices=new ChoiceStrip(this);ArrayList<String> k=new ArrayList<>(),l=new ArrayList<>();
        if(!bluetoothPermissionDenied){try{BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a!=null)for(BluetoothDevice d:a.getBondedDevices()){k.add(d.getAddress());l.add((d.getName()==null?"Printer":d.getName())+" • "+d.getAddress());}}catch(SecurityException e){bluetoothPermissionDenied=true;}}
        devices.setItems(k.toArray(new String[0]),l.toArray(new String[0]),k.isEmpty()?"":k.get(0));root.addView(devices,Ui.lpMatchWrap(this,10));
        Button print=Ui.button(this,"Надрукувати кошторис");print.setOnClickListener(v->go());root.addView(print,Ui.lpMatchWrap(this,12));
        status=Ui.muted(this,bluetoothPermissionDenied?"Доступ до Bluetooth не надано. Дозволь Nearby devices / Bluetooth для прямого друку.":"Для сумісності текст на чеку друкується транслітерацією; PDF залишається українською.");root.addView(status,Ui.lpMatchWrap(this,8));setContentView(s);
    }
    private void go(){
        if(bluetoothPermissionDenied){status.setText("Немає дозволу Bluetooth. Відкрий дозволи програми та увімкни Nearby devices / Bluetooth.");return;}
        String mac=devices.getSelectedKey();if(mac==null||mac.isEmpty()){status.setText("Не вибрано принтер.");return;}
        CommercialPlan p=new CommercialRepository(this).load(projectId);ElectricalProject ep=new FileProjectRepository(this).get(projectId);status.setText("Друк…");
        StableTaskRunner.run(this,"ESTIMATE_RECEIPT",()->{BluetoothThermalPrinter.print(mac,EstimateReceiptBuilder.build(p,ep==null?"Object":ep.displayName()));return true;},ok->status.setText("Кошторис надруковано."),e->status.setText("Помилка: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));
    }
}
