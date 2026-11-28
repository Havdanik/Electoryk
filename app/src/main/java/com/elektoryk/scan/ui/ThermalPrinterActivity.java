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
import android.widget.Toast;
import com.elektoryk.scan.model.CableCutList;
import com.elektoryk.scan.model.CableCutPiece;
import com.elektoryk.scan.printing.BluetoothThermalPrinter;
import com.elektoryk.scan.printing.LabelCommandBuilder;
import com.elektoryk.scan.storage.FieldWorkRepository;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class ThermalPrinterActivity extends Activity {
    private static final int REQ_BT=77;private volatile boolean printing;private boolean bluetoothPermissionDenied;private String projectId,pieceKey;private FieldWorkRepository repo;private CableCutList cuts;private ChoiceStrip devices,protocol,pieces;private TextView status;private final List<BluetoothDevice> bonded=new ArrayList<>();
    @Override protected void onCreate(Bundle b){super.onCreate(b);projectId=getIntent().getStringExtra("projectId");pieceKey=getIntent().getStringExtra("pieceKey");repo=new FieldWorkRepository(this);cuts=repo.loadCuts(projectId);if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);}else buildUi();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_BT){bluetoothPermissionDenied=!(g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED);buildUi();}}
    private void buildUi(){ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);root.addView(Ui.brandHeader(this,"ELEKTORYK • Labels","Парні етикетки на обидва кінці кабелю.","PRINT",true));root.addView(Ui.screenIntro(this,"ТЕРМОПРИНТЕР","Маркування кабелю","Вибери спарений Bluetooth-принтер і мову команд. Для максимальної сумісності прямий друк використовує ASCII-транслітерацію; у програмі назви лишаються українською."),Ui.lpMatchWrap(this,10));
        devices=new ChoiceStrip(this);refreshDevices();root.addView(Ui.title(this,"Принтер",18),Ui.lpMatchWrap(this,12));root.addView(devices);
        protocol=new ChoiceStrip(this);protocol.setItems(new String[]{"TSPL","ZPL","CPCL","ESC_POS"},new String[]{"TSPL / XPrinter / TSC","ZPL / Zebra","CPCL","ESC/POS"},"TSPL");root.addView(Ui.title(this,"Протокол",18),Ui.lpMatchWrap(this,12));root.addView(protocol);
        pieces=new ChoiceStrip(this);String[] keys=new String[cuts.pieces.size()],labels=new String[cuts.pieces.size()];String selected=cuts.pieces.isEmpty()?"":cuts.pieces.get(0).stableKey;for(int i=0;i<cuts.pieces.size();i++){CableCutPiece p=cuts.pieces.get(i);keys[i]=p.stableKey;labels[i]=p.code+" • "+p.cableKey+" • "+String.format(Locale.US,"%.1fm",p.cutLengthM);if(p.stableKey.equals(pieceKey))selected=p.stableKey;}pieces.setItems(keys,labels,selected);root.addView(Ui.title(this,"Відрізок",18),Ui.lpMatchWrap(this,12));root.addView(pieces);
        Button one=Ui.button(this,"Надрукувати пару A + B");one.setOnClickListener(v->printSelected());root.addView(one,Ui.lpMatchWrap(this,14));Button all=Ui.secondaryButton(this,"Надрукувати всі ненадруковані");all.setOnClickListener(v->printAll());root.addView(all,Ui.lpMatchWrap(this,8));Button reprint=Ui.secondaryButton(this,"Передрукувати всі");reprint.setOnClickListener(v->printBatch(cuts.pieces,false));root.addView(reprint,Ui.lpMatchWrap(this,8));Button png=Ui.secondaryButton(this,"PNG-лист етикеток / друк через фірмовий app");png.setOnClickListener(v->LabelSheetExporter.share(this,cuts.pieces,"ELEKTORYK"));root.addView(png,Ui.lpMatchWrap(this,8));status=Ui.muted(this,bluetoothPermissionDenied?"Доступ до Bluetooth не надано. Дозволь Nearby devices / Bluetooth, інакше список та прямий друк недоступні.":"Якщо принтера немає у списку — спочатку спаруй його в системних Bluetooth-налаштуваннях. BLE-only моделі можуть вимагати окремий драйвер виробника; для них використовуй PNG-лист через рідний app принтера.");root.addView(status,Ui.lpMatchWrap(this,12));root.addView(Ui.bottomNav(this,projectId,"ELECTRIC"),Ui.lpMatchWrap(this,18));setContentView(s);}
    private void refreshDevices(){bonded.clear();BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();List<String> keys=new ArrayList<>(),labels=new ArrayList<>();try{if(a!=null)for(BluetoothDevice d:a.getBondedDevices()){bonded.add(d);keys.add(d.getAddress());labels.add((d.getName()==null?"Bluetooth printer":d.getName())+" • "+d.getAddress());}}catch(SecurityException ignored){}devices.setItems(keys.toArray(new String[0]),labels.toArray(new String[0]),keys.isEmpty()?"":keys.get(0));}
    private CableCutPiece selected(){String k=pieces.getSelectedKey();for(CableCutPiece p:cuts.pieces)if(p.stableKey.equals(k))return p;return null;}
    private void printSelected(){CableCutPiece p=selected();if(p==null)return;List<CableCutPiece> l=new ArrayList<>();l.add(p);printBatch(l,true);}
    private void printAll(){List<CableCutPiece> l=new ArrayList<>();for(CableCutPiece p:cuts.pieces)if(!p.labelsPrinted)l.add(p);printBatch(l,true);}
    private void printBatch(List<CableCutPiece> list,boolean mark){
        if(bluetoothPermissionDenied){status.setText("Немає дозволу Bluetooth. Відкрий дозволи програми та увімкни Nearby devices / Bluetooth.");return;}
        if(printing){status.setText("Попереднє завдання друку ще виконується.");return;}
        if(list.isEmpty()){Toast.makeText(this,"Немає етикеток для друку",Toast.LENGTH_SHORT).show();return;}
        String mac=devices.getSelectedKey();if(mac==null||mac.isEmpty()){status.setText("Не вибраний Bluetooth-принтер.");return;}
        LabelCommandBuilder.Protocol proto;try{proto=LabelCommandBuilder.Protocol.valueOf(protocol.getSelectedKey());}catch(Exception e){proto=LabelCommandBuilder.Protocol.TSPL;}
        final LabelCommandBuilder.Protocol finalProto=proto;
        printing=true;status.setText("Друк: "+list.size()+" відрізків… Не натискай друк повторно.");
        StableTaskRunner.run(this,"THERMAL_PRINT",()->{
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            for(CableCutPiece p:list)out.write(LabelCommandBuilder.pair(p,finalProto));
            BluetoothThermalPrinter.print(mac,out.toByteArray());
            if(mark){for(CableCutPiece p:list)p.labelsPrinted=true;repo.saveCuts(cuts);}
            return Boolean.TRUE;
        },ok->{
            printing=false;
            status.setText("Готово: надруковано "+(list.size()*2)+" етикеток (A+B).");
        },e->{printing=false;status.setText("Помилка принтера: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())+". Дані не позначені як надруковані — можна безпечно повторити.");});
    }
}
