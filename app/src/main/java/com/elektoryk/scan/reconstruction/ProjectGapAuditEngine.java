package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.*;

public class ProjectGapAuditEngine {
    public ProjectGapAuditReport audit(ElectricalProject project,SceneSnapshot scene,FloorPlan floor,CableCutList cuts,FieldWorkPlan field,CommercialPlan commercial,EvidenceIndex evidence,RevisionHistory revisions,ClientApproval approval){
        ProjectGapAuditReport r=new ProjectGapAuditReport();
        if(project==null){add(r,GapAuditItem.Severity.CRITICAL,"Об'єкт","Немає об'єкта","Проєкт не завантажився.","Відновити backup або створити об'єкт");finish(r);return r;}
        if(project.approximateAreaM2<=0)add(r,GapAuditItem.Severity.WARNING,"ТЗ","Немає контрольної площі","Важче зрозуміти, чи скан пропустив частину квартири.","Задати площу");
        if(scene==null||scene.roomBounds==null||!scene.roomBounds.isUsable())add(r,GapAuditItem.Severity.CRITICAL,"Скан","Немає придатного Smart Scan","Без геометрії не можна надійно будувати план і траси.","Зробити Smart Scan");
        else{
            if(scene.directionCoveragePercent<60)add(r,GapAuditItem.Severity.WARNING,"Скан","Мало ракурсів",scene.directionCoveragePercent+"% покриття напрямків.","Досканувати квартиру");
            if(!scene.scanGaps.isEmpty())add(r,GapAuditItem.Severity.WARNING,"Скан","Є слабкі/пропущені зони","Зон: "+scene.scanGaps.size()+".","Пересканувати проблемні місця");
            if(scene.precisionReport==null||scene.precisionReport.anchorsUsed==0)add(r,GapAuditItem.Severity.WARNING,"Точність","Немає контрольного лазерного заміру","Скан не має зовнішньої метричної перевірки.","Додати 2–4 контрольні заміри");
            else if(scene.precisionReport.score<72)add(r,GapAuditItem.Severity.WARNING,"Точність","Точність нижче бажаної","Score "+scene.precisionReport.score+"/100.","Перевірити контрольні розміри/скан");
            if(scene.apartmentPlan==null||scene.apartmentPlan.rooms.isEmpty())add(r,GapAuditItem.Severity.CRITICAL,"План","Multi-room план не готовий","Немає робочого плану квартири.","Побудувати/виправити план");
            else if(!scene.apartmentPlan.missingZoneHints.isEmpty())add(r,GapAuditItem.Severity.WARNING,"План","Є підозра на пропущені кімнати","Підказок: "+scene.apartmentPlan.missingZoneHints.size()+".","Перевірити план");
        }

        ElectricalDesignReport qa=new ElectricalDesignAnalyzer().analyze(scene==null?null:scene.apartmentPlan,floor);
        if(qa.panelCount==0)add(r,GapAuditItem.Severity.CRITICAL,"Електрика","Не поставлено щит","Траси не мають точки старту.","Додати щит");
        if(qa.unassignedCircuitPoints>0)add(r,GapAuditItem.Severity.CRITICAL,"Електрика","Є точки без групи","Кількість: "+qa.unassignedCircuitPoints+".","Призначити групи");
        if(qa.unboundWallPoints>0)add(r,GapAuditItem.Severity.WARNING,"Електрика","Настінні точки без стіни","Кількість: "+qa.unboundWallPoints+".","Прив'язати до стін");
        if(qa.duplicateCandidates>0)add(r,GapAuditItem.Severity.WARNING,"Електрика","Можливі дублікати точок","Кандидатів: "+qa.duplicateCandidates+".","Перевірити редактор");

        if(cuts==null||cuts.pieces.isEmpty())add(r,GapAuditItem.Severity.CRITICAL,"Кабель","Немає CUT-листа","Немає фізичного списку відрізків для монтажу.","Порахувати траси/CUT-лист");
        else{
            int noSpool=0,noLabels=0,notInstalled=0;for(CableCutPiece p:cuts.pieces){if(p.spool==null||p.spool.isEmpty())noSpool++;if(!p.labelsPrinted)noLabels++;if(!p.installed)notInstalled++;}
            if(noSpool>0)add(r,GapAuditItem.Severity.WARNING,"Кабель","Не всі відрізки розкладені по бухтах","Кількість: "+noSpool+".","Оптимізувати нарізку");
            if(noLabels>0)add(r,GapAuditItem.Severity.WARNING,"Маркування","Не всі кабелі промарковані","Кількість: "+noLabels+".","Надрукувати етикетки");
            if(notInstalled>0)add(r,GapAuditItem.Severity.WARNING,"Монтаж","Не всі відрізки позначені як змонтовані","Кількість: "+notInstalled+".","Закрити монтажний чекліст");
        }

        if(field==null||field.circuits.isEmpty())add(r,GapAuditItem.Severity.CRITICAL,"Щит","Немає паспортів груп","Немає підтвердженого захисту/commissioning.","Сформувати групи");
        else{int unverified=0,pending=0,fail=0;for(CircuitProtectionPlan c:field.circuits){if(!c.verifiedByElectrician)unverified++;if("FAIL".equals(c.testStatus))fail++;else if(!"PASS".equals(c.testStatus))pending++;}if(unverified>0)add(r,GapAuditItem.Severity.CRITICAL,"Щит","Є непідтверджений захист","Груп: "+unverified+".","Підтвердити електриком");if(fail>0)add(r,GapAuditItem.Severity.CRITICAL,"Commissioning","Є групи зі статусом Проблема","Груп: "+fail+".","Виправити і повторити перевірку");if(pending>0)add(r,GapAuditItem.Severity.WARNING,"Commissioning","Не всі групи перевірені","Груп: "+pending+".","Завершити вимірювання");}

        if(commercial==null||commercial.lines.isEmpty())add(r,GapAuditItem.Severity.WARNING,"Кошторис","Немає комерційного плану","Технічна частина не переведена в закупівлю/ціну.","Відкрити кошторис");
        else{int noPrice=0,notReceived=0;for(CommercialLine l:commercial.lines){if(l.includeInEstimate&&(l.category==CommercialLine.Category.LABOR||l.owner==CommercialLine.ProcurementOwner.MASTER)&&l.unitCost<=0)noPrice++;if(l.category==CommercialLine.Category.MATERIAL&&l.owner==CommercialLine.ProcurementOwner.MASTER&&l.purchaseQty>0&&!l.received)notReceived++;}if(noPrice>0)add(r,GapAuditItem.Severity.WARNING,"Кошторис","Не всі ціни заповнені","Позицій: "+noPrice+".","Заповнити ціни/ставки");if(notReceived>0)add(r,GapAuditItem.Severity.WARNING,"Закупівля","Не все отримано на об'єкт","Позицій: "+notReceived+".","Закрити закупівлю");}

        int evidenceCount=evidence==null?0:evidence.items.size();if(evidenceCount==0)add(r,GapAuditItem.Severity.WARNING,"As-built","Немає фото фактичного монтажу","Приховані роботи не задокументовані.","Додати фото");else{boolean hidden=false,panel=false,finished=false;for(EvidenceItem e:evidence.items){if(e.category==EvidenceItem.Category.HIDDEN_WORK)hidden=true;if(e.category==EvidenceItem.Category.PANEL)panel=true;if(e.category==EvidenceItem.Category.FINISHED)finished=true;}if(!hidden)add(r,GapAuditItem.Severity.WARNING,"As-built","Немає фото прихованих робіт","Після закриття штроб це вже не відновити.","Додати фото до закриття");if(!panel)add(r,GapAuditItem.Severity.OPTIONAL,"As-built","Немає фото щита","Корисно для сервісу й клієнта.","Додати фото щита");if(!finished)add(r,GapAuditItem.Severity.OPTIONAL,"As-built","Немає фінального фото","VIP пакет буде менш наочний.","Додати готовий результат");}

        if(revisions==null||revisions.revisions.isEmpty())add(r,GapAuditItem.Severity.OPTIONAL,"Версії","Немає базової ревізії V1","Складніше довести, що саме змінилось після погодження.","Зберегти V1");
        if(approval==null||!approval.planApproved)add(r,GapAuditItem.Severity.OPTIONAL,"Клієнт","План не позначено погодженим","Немає внутрішнього запису погодження.","Зафіксувати погодження");
        if(approval==null||!approval.estimateApproved)add(r,GapAuditItem.Severity.OPTIONAL,"Клієнт","Кошторис не позначено погодженим","Немає внутрішнього запису погодження ціни.","Зафіксувати погодження");
        if(r.items.isEmpty())add(r,GapAuditItem.Severity.OK,"Проєкт","Критичних прогалин не знайдено","Основний цикл виглядає заповненим.","Фінальний професійний огляд");
        finish(r);return r;
    }
    private void add(ProjectGapAuditReport r,GapAuditItem.Severity s,String area,String title,String detail,String action){r.items.add(new GapAuditItem(s,area,title,detail,action));}
    private void finish(ProjectGapAuditReport r){int penalty=r.count(GapAuditItem.Severity.CRITICAL)*12+r.count(GapAuditItem.Severity.WARNING)*5+r.count(GapAuditItem.Severity.OPTIONAL)*2;r.score=Math.max(0,100-penalty);}
}
