package com.elektoryk.scan.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;

import com.elektoryk.scan.model.RoomFinishCatalog;
import com.elektoryk.scan.model.VisualAssetPreset;
import com.elektoryk.scan.model.WallFinishCatalog;
import com.elektoryk.scan.storage.VisualAssetRepository;

import java.io.InputStream;
import java.util.*;

/** Searchable offline visual library used by the 3D workspace. */
public class AssetLibraryActivity extends Activity {
    private static final int PICK=812;
    private VisualAssetRepository repo;
    private LinearLayout results;
    private EditText search;
    private ChoiceStrip filter;
    private TextView count;

    @Override protected void onCreate(Bundle b){super.onCreate(b);repo=new VisualAssetRepository(this);build();}

    private void build(){
        ScrollView s=new ScrollView(this);s.setBackgroundColor(Ui.BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,14),Ui.dp(this,30));s.addView(root);
        root.addView(Ui.brandHeader(this,"ELEKTORYK • Бібліотека","Матеріали, електрика, світло, меблі, техніка й сантехніка — офлайн.","ASSETS",true));
        root.addView(Ui.screenIntro(this,"INTERIOR STUDIO LIBRARY","Швидко знайди й постав потрібне","Пошук працює по назві, типу, групі та тегах. Вбудовані моделі легкі й параметричні: їх можна рухати, масштабувати, дублювати й замінювати без важких GLB-файлів."),Ui.lpMatchWrap(this,10));

        LinearLayout searchCard=Ui.softCard(this);searchCard.addView(Ui.title(this,"Пошук і фільтр",16));
        search=new EditText(this);search.setHint("Напр. розетка, трек, TV, кухня, ліжко…");search.setSingleLine(true);search.setTextColor(Ui.TEXT);search.setHintTextColor(Ui.MUTED);search.setBackgroundColor(Ui.CARD_SOFT);search.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8));searchCard.addView(search,Ui.lpMatchWrap(this,4));
        filter=new ChoiceStrip(this);filter.setItems(new String[]{"ALL","ELECTRICAL","FURNITURE"},new String[]{"Все","Електрика / світло","Меблі / інтер'єр"},"ALL");searchCard.addView(filter,Ui.lpMatchWrap(this,4));
        count=Ui.muted(this,"");searchCard.addView(count,Ui.lpMatchWrap(this,3));root.addView(searchCard,Ui.lpMatchWrap(this,8));

        root.addView(Ui.title(this,"Матеріали",19),Ui.lpMatchWrap(this,12));
        LinearLayout materialCard=Ui.softCard(this);materialCard.addView(Ui.muted(this,"Стіни: "+WallFinishCatalog.PRESETS.length+" • підлога: "+RoomFinishCatalog.FLOORS.length+" • стеля: "+RoomFinishCatalog.CEILINGS.length+" • плінтуси: "+RoomFinishCatalog.SKIRT_KEYS.length));
        root.addView(materialCard,Ui.lpMatchWrap(this,4));

        Button imp=Ui.button(this,"+ Встановити .json пакет моделей");imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK);});root.addView(imp,Ui.lpMatchWrap(this,10));
        root.addView(Ui.muted(this,"Власний пакет може містити назву, тип, групу, теги, габарити, місце монтажу, стартову висоту й крок прив'язки. Імпорт не змінює технічні електричні дані проєкту."),Ui.lpMatchWrap(this,6));

        results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);root.addView(results,Ui.lpMatchWrap(this,8));
        TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence c,int st,int co,int af){}public void onTextChanged(CharSequence c,int st,int be,int co){render();}public void afterTextChanged(Editable e){}};search.addTextChangedListener(watcher);filter.setListener(k->render());
        render();setContentView(s);
    }

    private void render(){
        if(results==null)return;results.removeAllViews();String cat=filter==null?"ALL":filter.getSelectedKey();if("ALL".equals(cat))cat="";String q=search==null?"":search.getText().toString();List<VisualAssetPreset> all=repo.search(q,cat);count.setText("Знайдено: "+all.size()+" • вбудовані й встановлені пакети");
        LinkedHashMap<String,List<VisualAssetPreset>> groups=new LinkedHashMap<>();for(VisualAssetPreset a:all){String g=a.group==null||a.group.isEmpty()?"Інше":a.group;List<VisualAssetPreset> list=groups.get(g);if(list==null){list=new ArrayList<>();groups.put(g,list);}list.add(a);}for(Map.Entry<String,List<VisualAssetPreset>> e:groups.entrySet()){results.addView(Ui.title(this,e.getKey(),19),Ui.lpMatchWrap(this,12));for(VisualAssetPreset a:e.getValue())addAssetCard(a);}if(all.isEmpty())results.addView(Ui.muted(this,"Нічого не знайдено. Спробуй інше слово або скинь фільтр на «Все»."),Ui.lpMatchWrap(this,12));
    }

    private void addAssetCard(VisualAssetPreset a){LinearLayout c=Ui.softCard(this);c.addView(Ui.title(this,a.label,16));String type="ELECTRICAL".equals(a.category)?a.pointType:a.furnitureType;String extra=(a.defaultMountHeightM>0?String.format(Locale.US," • H %.0f см",a.defaultMountHeightM*100):"");c.addView(Ui.muted(this,(a.builtIn?"Вбудована":"Встановлена")+" • "+type+" • "+a.placement+String.format(Locale.US," • %.0f×%.0f×%.0f см",a.widthM*100,a.depthM*100,a.heightM*100)+extra+" • "+a.id));results.addView(c,Ui.lpMatchWrap(this,4));}

    @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r!=PICK||c!=RESULT_OK||d==null||d.getData()==null)return;final android.net.Uri uri=d.getData();Toast.makeText(this,"Перевіряю пакет моделей…",Toast.LENGTH_SHORT).show();StableTaskRunner.run(this,"ASSET_IMPORT",()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException("Файл недоступний");return repo.importPack(in);}},n->{Toast.makeText(this,"Встановлено моделей: "+n,Toast.LENGTH_LONG).show();render();},e->Toast.makeText(this,"Не вдалося встановити пакет: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),Toast.LENGTH_LONG).show());}
}
