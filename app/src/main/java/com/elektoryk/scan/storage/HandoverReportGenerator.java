package com.elektoryk.scan.storage;

import android.content.Context;
import com.elektoryk.scan.model.*;
import com.elektoryk.scan.reconstruction.InstallationRoutingEngine;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class HandoverReportGenerator {
    public File generate(Context c,String projectId)throws Exception{
        ElectricalProject p=new FileProjectRepository(c).get(projectId);
        SceneSnapshot scene=new SceneRepository(c).loadScene(projectId);
        FloorPlan floor=new FloorPlanRepository(c).load(projectId);
        FieldWorkRepository fw=new FieldWorkRepository(c);
        CableCutList cuts=fw.loadCuts(projectId);
        FieldWorkPlan field=fw.loadPlan(projectId);
        CommercialPlan commercial=new CommercialRepository(c).load(projectId);
        RevisionHistory revisions=new RevisionRepository(c).load(projectId);
        EvidenceIndex evidence=new EvidenceRepository(c).load(projectId);
        ApprovalRepository approvalRepo=new ApprovalRepository(c);ClientApproval approval=approvalRepo.load(projectId);
        String signatureData="";File signatureFile=approvalRepo.signatureFile(projectId);if(signatureFile.exists())try{byte[] sb=readAll(signatureFile);signatureData="data:image/png;base64,"+java.util.Base64.getEncoder().encodeToString(sb);}catch(Exception ignored){}
        InstallationRoutingResult routing=(p!=null&&scene!=null&&scene.apartmentPlan!=null)
                ?new InstallationRoutingEngine().build(p,scene.apartmentPlan,floor):new InstallationRoutingResult();
        if(p==null)throw new IllegalArgumentException("Project not found");
        File dir=new File(c.getCacheDir(),"exports");if(!dir.exists())dir.mkdirs();
        File out=new File(dir,safe(p.displayName())+"_HANDOVER_"+System.currentTimeMillis()+".html");
        AtomicFileStore.writeText(out,html(p,scene,floor,cuts,field,routing,commercial,revisions,evidence,approval,signatureData));
        return out;
    }

    private String html(ElectricalProject p,SceneSnapshot scene,FloorPlan floor,CableCutList cuts,FieldWorkPlan field,InstallationRoutingResult routing,CommercialPlan commercial,RevisionHistory revisions,EvidenceIndex evidence,ClientApproval approval,String signatureData){
        int verified=0,testPass=0,testFail=0,testPending=0;
        for(CircuitProtectionPlan c:field.circuits){
            if(c.verifiedByElectrician)verified++;
            if("PASS".equals(c.testStatus))testPass++;else if("FAIL".equals(c.testStatus))testFail++;else testPending++;
        }
        int installed=0,printed=0,cutDone=0;
        for(CableCutPiece x:cuts.pieces){if(x.installed)installed++;if(x.labelsPrinted)printed++;if(x.cutDone)cutDone++;}
        String date=new SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date());
        StringBuilder h=new StringBuilder();
        h.append("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>");
        h.append("body{margin:0;background:#0b0b0d;color:#f4f4f5;font-family:Arial,sans-serif}.page{max-width:980px;margin:auto;padding:38px}.hero{border:1px solid #76531d;border-radius:24px;padding:28px;background:linear-gradient(135deg,#1b1812,#101013)}.brand{font-size:42px;font-weight:900;letter-spacing:2px;color:#e2b136}.sub{color:#aaaab3}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin:18px 0}.card{background:#17171b;border:1px solid #34343c;border-radius:16px;padding:16px}.v{font-size:24px;font-weight:800;color:#e2b136}.tag{display:inline-block;padding:5px 10px;border:1px solid #76531d;border-radius:999px;color:#e2b136;font-weight:700;font-size:12px}.ok{color:#39d98a}.warn{color:#ffc400}.bad{color:#ff5a5f}table{width:100%;border-collapse:collapse;margin-top:10px}th,td{text-align:left;border-bottom:1px solid #333;padding:9px 6px;font-size:13px}th{color:#e2b136}.foot{margin-top:28px;padding-top:18px;border-top:1px solid #333;color:#888;font-size:12px}</style></head><body><div class='page'>");
        h.append("<div class='hero'><div class='tag'>PROJECT PASSPORT</div><div class='brand'>ELEKTORYK</div><h1>").append(esc(p.displayName())).append("</h1><div class='sub'>Паспорт електромонтажного об'єкта • ").append(date).append("</div></div>");

        h.append("<div class='grid'>")
                .append(stat(p.approximateAreaM2>0?fmt(p.approximateAreaM2)+" м²":"—","Площа"))
                .append(stat(String.valueOf(floor==null?0:floor.points.size()),"Електроточки"))
                .append(stat(fmt(routing.totalPurchaseCableM)+" м","Кабель"))
                .append(stat(String.valueOf(field.circuits.size()),"Групи"))
                .append("</div>");

        h.append("<h2>Стан здачі</h2><div class='card'>");
        h.append(line("Групи підтверджені електриком",verified+" / "+field.circuits.size(),verified==field.circuits.size()&&!field.circuits.isEmpty(),false));
        h.append(line("Кабель нарізано",cutDone+" / "+cuts.pieces.size(),cutDone==cuts.pieces.size()&&!cuts.pieces.isEmpty(),false));
        h.append(line("Кабельні етикетки",printed+" / "+cuts.pieces.size(),printed==cuts.pieces.size()&&!cuts.pieces.isEmpty(),false));
        h.append(line("Відрізки позначені як змонтовані",installed+" / "+cuts.pieces.size(),installed==cuts.pieces.size()&&!cuts.pieces.isEmpty(),false));
        if(scene!=null&&scene.precisionReport!=null)h.append(line("Контроль точності",scene.precisionReport.score+"/100 • ≈ "+fmt(scene.precisionReport.estimatedPlanErrorCm)+" см",scene.precisionReport.score>=72,false));
        h.append(line("Commissioning",testPass+" OK / "+testFail+" problem / "+testPending+" pending",testPass==field.circuits.size()&&!field.circuits.isEmpty(),testFail>0));
        h.append("</div>");

        h.append("<h2>Щит і групи</h2><div class='card'><table><tr><th>Група</th><th>Кабель</th><th>Навантаження</th><th>Автомат</th><th>Фаза</th><th>Захист</th><th>Статус</th></tr>");
        for(CircuitProtectionPlan x:field.circuits){
            h.append("<tr><td>").append(esc(x.name)).append("</td><td>").append(esc(x.cableKey)).append("</td><td>").append(fmt(x.plannedLoadW/1000.0)).append(" kW</td><td>").append(esc(x.suggestedCurve)).append(x.suggestedBreakerA).append(" A • ").append(x.poles).append("P</td><td>").append(esc(x.phase)).append("</td><td>").append(esc(x.protection)).append(("RCBO".equals(x.protection)||"RCD_MCB".equals(x.protection))?" • "+x.residualCurrentMa+" mA":"").append("</td><td>").append(x.verifiedByElectrician?"Підтверджено":"Чернетка").append("</td></tr>");
        }
        h.append("</table></div>");

        h.append("<h2>Commissioning / фактичні вимірювання</h2><div class='card'><table><tr><th>Група</th><th>Статус</th><th>V</th><th>Ізоляція MΩ</th><th>RCD ms</th><th>Петля Ω</th></tr>");
        for(CircuitProtectionPlan x:field.circuits){
            h.append("<tr><td>").append(esc(x.name)).append("</td><td>").append(esc(x.testStatus)).append("</td><td>").append(x.measuredVoltageV>0?fmt(x.measuredVoltageV):"—").append("</td><td>").append(x.insulationMohm>0?fmt(x.insulationMohm):"—").append("</td><td>").append(x.rcdTripMs>0?fmt(x.rcdTripMs):"—").append("</td><td>").append(x.loopImpedanceOhm>0?fmt(x.loopImpedanceOhm):"—").append("</td></tr>");
        }
        h.append("</table><div class='sub' style='margin-top:12px'>Записані значення є журналом фактичних вимірювань у проєкті та не замінюють вимоги до офіційних випробувань/сертифікації, якщо вони потрібні.</div></div>");

        h.append("<h2>Монтажні матеріали</h2><div class='grid'>");
        for(Map.Entry<String,Double> e:routing.materials.cableM.entrySet())h.append(stat(fmt(e.getValue())+" м",e.getKey()));
        h.append(stat(String.valueOf(routing.materials.wallPenetrations),"Проходки")).append(stat(String.valueOf(routing.materials.fasteners),"Кріплення")).append("</div>");

        commercial.recalc();
        h.append("<h2>Кошторис / закупівля</h2><div class='grid'>")
                .append(stat(fmt(commercial.materialCost)+" грн","Собівартість матеріалів"))
                .append(stat(fmt(commercial.materialClient)+" грн","Матеріали клієнту"))
                .append(stat(fmt(commercial.laborClient)+" грн","Роботи"))
                .append(stat(fmt(commercial.total)+" грн","Разом"))
                .append("</div>");
        int purchaseNeed=0,purchaseReceived=0;for(CommercialLine cl:commercial.lines)if(cl.category==CommercialLine.Category.MATERIAL&&cl.owner==CommercialLine.ProcurementOwner.MASTER&&cl.purchaseQty>0){purchaseNeed++;if(cl.received)purchaseReceived++;}
        h.append("<div class='card'>").append(line("Закупівля отримана",purchaseReceived+" / "+purchaseNeed,purchaseNeed==0||purchaseReceived==purchaseNeed,false)).append("</div>");

        h.append("<h2>As-built / версії / погодження</h2><div class='card'>");
        h.append(line("Фото / докази",String.valueOf(evidence.items.size()),!evidence.items.isEmpty(),false));
        h.append(line("Ревізії проєкту",String.valueOf(revisions.revisions.size()),!revisions.revisions.isEmpty(),false));
        h.append(line("План погоджено",approval.planApproved?"Так":"Ні",approval.planApproved,false));
        h.append(line("Кошторис погоджено",approval.estimateApproved?"Так":"Ні",approval.estimateApproved,false));
        h.append(line("Роботи прийнято",approval.handoverAccepted?"Так":"Ні",approval.handoverAccepted,false));
        if(approval.revisionId!=null&&!approval.revisionId.isEmpty())h.append("<div class='sub' style='margin-top:8px'>Погоджена ревізія ID: ").append(esc(approval.revisionId)).append("</div>");
        if(signatureData!=null&&!signatureData.isEmpty())h.append("<div style='margin-top:14px;background:#f5f5f5;border-radius:12px;padding:10px;display:inline-block'><img alt='Підпис клієнта' src='").append(signatureData).append("' style='max-width:340px;max-height:150px'></div>");
        h.append("<div class='sub' style='margin-top:12px'>Вбудоване погодження є внутрішнім записом проєкту і не є КЕП/кваліфікованим електронним підписом.</div></div>");

        h.append("<h2>Кабельна карта</h2><div class='card'><table><tr><th>Код</th><th>Звідки → куди</th><th>Кабель</th><th>Автомат</th><th>CUT</th><th>Бухта</th><th>Монтаж</th></tr>");
        for(CableCutPiece x:cuts.pieces){
            h.append("<tr><td>").append(esc(x.code)).append("</td><td>").append(esc(x.fromLabel)).append(" → ").append(esc(x.toLabel)).append("</td><td>").append(esc(x.cableKey)).append("</td><td>").append(esc(x.protectionTag)).append("</td><td>").append(fmt(x.cutLengthM)).append(" м</td><td>").append(esc(x.spool)).append("</td><td>").append(x.installed?"✓":"—").append("</td></tr>");
        }
        h.append("</table></div>");
        h.append("<div class='foot'>ELEKTORYK • цифровий паспорт об'єкта. Робочі підказки щодо захисту вважаються фінальними лише після позначки «підтверджено електриком». Зберігайте цей файл разом із фінальним FULL .elektoryk snapshot.</div></div></body></html>");
        return h.toString();
    }

    private byte[] readAll(File f)throws Exception{if(f==null||!f.isFile()||f.length()>5L*1024L*1024L)throw new java.io.IOException("Signature file invalid");try(java.io.InputStream in=new java.io.FileInputStream(f);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;long total=0;while((n=in.read(b))>0){total+=n;if(total>5L*1024L*1024L)throw new java.io.IOException("Signature too large");out.write(b,0,n);}return out.toByteArray();}}
    private String stat(String v,String l){return "<div class='card'><div class='v'>"+esc(v)+"</div><div class='sub'>"+esc(l)+"</div></div>";}
    private String line(String a,String b,boolean ok,boolean bad){return "<div style='display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #2d2d33'><span>"+esc(a)+"</span><b class='"+(bad?"bad":ok?"ok":"warn")+"'>"+esc(b)+"</b></div>";}
    private String esc(String s){if(s==null)return "";return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private String safe(String s){return s==null?"project":s.replaceAll("[^a-zA-Z0-9А-Яа-яІіЇїЄє_-]+","_");}
    private String fmt(double v){return String.format(Locale.US,"%.1f",v);}
}
