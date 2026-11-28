package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.*;

public class ProjectWorkflowEngine {

    public ProjectWorkflowSnapshot build(ElectricalProject project,
                                         SceneSnapshot scene,
                                         FloorPlan floor,
                                         CableCutList cuts,
                                         FieldWorkPlan field) {
        return build(project,scene,floor,cuts,field,null,null,null);
    }

    public ProjectWorkflowSnapshot build(ElectricalProject project,
                                         SceneSnapshot scene,
                                         FloorPlan floor,
                                         CableCutList cuts,
                                         FieldWorkPlan field,
                                         CommercialPlan commercial,
                                         EvidenceIndex evidence,
                                         ClientApproval approval) {
        ProjectWorkflowSnapshot out = new ProjectWorkflowSnapshot();

        WorkflowStageState setup = state(ProjectWorkflowStage.SETUP);
        if (project == null) {
            setup.status = WorkflowStageState.Status.BLOCKED;
            setup.blockers.add("Об'єкт не знайдено.");
        } else {
            int p = 35;
            if (project.approximateAreaM2 > 0) p += 30;
            if (project.roomCount > 0) p += 15;
            if (project.settings.plannedGroups > 0) p += 20;
            setup.progress = Math.min(100, p);
            setup.status = setup.progress >= 80 ? WorkflowStageState.Status.COMPLETE : WorkflowStageState.Status.IN_PROGRESS;
            setup.primaryAction = "Параметри об'єкта";
            if (project.approximateAreaM2 <= 0) setup.warnings.add("Не задана контрольна площа.");
        }
        out.stages.add(setup);

        WorkflowStageState scan = state(ProjectWorkflowStage.SCAN);
        scan.primaryAction = "Smart Scan";
        if (project == null) block(scan, "Спочатку створи об'єкт.");
        else if (scene == null) {
            scan.status = WorkflowStageState.Status.READY;
            scan.progress = 0;
        } else if (scene.roomBounds == null || !scene.roomBounds.isUsable()) {
            scan.status = WorkflowStageState.Status.IN_PROGRESS;
            scan.progress = Math.max(15, Math.min(55, scene.directionCoveragePercent));
            scan.blockers.add("Скан є, але межі приміщення ще ненадійні.");
        } else {
            scan.progress = Math.max(70, Math.min(100, scene.directionCoveragePercent + 20));
            scan.status = scan.progress >= 85 ? WorkflowStageState.Status.COMPLETE : WorkflowStageState.Status.IN_PROGRESS;
            if (scene.scanGaps.size() > 0) scan.warnings.add("Є непроскановані/слабкі зони: " + scene.scanGaps.size() + ".");
        }
        out.stages.add(scan);

        WorkflowStageState plan = state(ProjectWorkflowStage.PLAN);
        plan.primaryAction = "Multi-room план";
        if (!scan.complete()) block(plan, "Спочатку потрібен придатний Smart Scan.");
        else if (scene == null || scene.apartmentPlan == null || scene.apartmentPlan.rooms.isEmpty()) {
            plan.status = WorkflowStageState.Status.READY;
        } else {
            int p = 55;
            if (!scene.apartmentPlan.walls.isEmpty()) p += 15;
            if (!scene.apartmentPlan.openings.isEmpty()) p += 10;
            if (scene.areaCheck != null && scene.areaCheck.status == AreaCheckResult.Status.MATCH) p += 20;
            plan.progress = Math.min(100, p);
            plan.status = p >= 80 ? WorkflowStageState.Status.COMPLETE : WorkflowStageState.Status.IN_PROGRESS;
            if (scene.apartmentPlan.missingZoneHints.size() > 0) plan.warnings.add("Є підозра на пропущені зони.");
            if (scene.latestAutoApartmentPlan != null) plan.warnings.add("Є новий автоплан, який ще не прийнятий.");
        }
        out.stages.add(plan);

        WorkflowStageState electrical = state(ProjectWorkflowStage.ELECTRICAL);
        electrical.primaryAction = "Редактор електрики";
        if (!plan.complete()) block(electrical, "Спочатку заверши план квартири.");
        else {
            int total = floor == null ? 0 : floor.points.size();
            int panel = 0, assigned = 0, wallBound = 0;
            if (floor != null) for (ElectricalPoint p : floor.points) {
                if (p.type == ElectricalPoint.Type.PANEL) panel++;
                else {
                    if (p.circuitNo > 0) assigned++;
                    if (p.wallId != null && !p.wallId.isEmpty()) wallBound++;
                }
            }
            if (total == 0) {
                electrical.status = WorkflowStageState.Status.READY;
                electrical.progress = 0;
            } else {
                int nonPanel = Math.max(0, total - panel);
                int p = 25;
                if (panel == 1) p += 25;
                if (nonPanel > 0) p += (int)Math.round(25.0 * assigned / nonPanel);
                if (nonPanel > 0) p += (int)Math.round(25.0 * wallBound / nonPanel);
                electrical.progress = Math.min(100, p);
                electrical.status = p >= 85 ? WorkflowStageState.Status.COMPLETE : WorkflowStageState.Status.IN_PROGRESS;
                if (panel == 0) electrical.blockers.add("Не поставлено щит.");
                if (panel > 1) electrical.warnings.add("На плані кілька щитів.");
                if (nonPanel > assigned) electrical.warnings.add("Є точки без номера групи: " + (nonPanel-assigned) + ".");
            }
        }
        out.stages.add(electrical);

        WorkflowStageState routing = state(ProjectWorkflowStage.ROUTING);
        routing.primaryAction = "Траси / метраж";
        InstallationRoutingResult routingResult = null;
        if (!electrical.complete()) block(routing, "Спочатку прив'яжи щит і групи.");
        else {
            try {
                routingResult = new InstallationRoutingEngine().build(project, scene.apartmentPlan, floor);
            } catch (Exception e) {
                routing.blockers.add("Помилка розрахунку трас: " + e.getClass().getSimpleName());
            }
            if (routingResult == null || routingResult.circuitRoutes.isEmpty()) {
                routing.status = routing.blockers.isEmpty() ? WorkflowStageState.Status.READY : WorkflowStageState.Status.IN_PROGRESS;
                routing.progress = routing.blockers.isEmpty() ? 0 : 20;
            } else {
                int p = 70;
                if (routingResult.warnings.isEmpty()) p += 20;
                if (routingResult.totalPurchaseCableM > 0) p += 10;
                routing.progress = Math.min(100,p);
                routing.status = p >= 85 ? WorkflowStageState.Status.COMPLETE : WorkflowStageState.Status.IN_PROGRESS;
                for (String w : routingResult.warnings) routing.warnings.add(w);
            }
        }
        out.stages.add(routing);

        WorkflowStageState materials = state(ProjectWorkflowStage.MATERIALS);
        materials.primaryAction = "CUT-лист / бухти / захист";
        if (!routing.complete()) block(materials, "Спочатку порахуй траси.");
        else if (cuts == null || cuts.pieces.isEmpty()) {
            materials.status = WorkflowStageState.Status.READY;
        } else {
            int withSpool = 0;
            for (CableCutPiece p : cuts.pieces) if (p.spool != null && !p.spool.isEmpty()) withSpool++;
            int verified = 0, circuits = field == null ? 0 : field.circuits.size();
            if (field != null) for (CircuitProtectionPlan c : field.circuits) if (c.verifiedByElectrician) verified++;
            int p = 50;
            if (!cuts.pieces.isEmpty()) p += (int)Math.round(25.0 * withSpool / cuts.pieces.size());
            if (circuits > 0) p += (int)Math.round(25.0 * verified / circuits);
            materials.progress = Math.min(100, p);
            materials.status = p >= 90 ? WorkflowStageState.Status.COMPLETE : WorkflowStageState.Status.IN_PROGRESS;
            if (withSpool < cuts.pieces.size()) materials.warnings.add("Не всі відрізки розкладені по бухтах.");
            if (circuits > verified) materials.warnings.add("Непідтверджених груп захисту: " + (circuits-verified) + ".");
        }
        out.stages.add(materials);

        WorkflowStageState commercialStage = state(ProjectWorkflowStage.COMMERCIAL);
        commercialStage.primaryAction = "Кошторис / закупівля";
        if (!routing.complete()) block(commercialStage, "Спочатку потрібні технічні кількості та траси.");
        else if (commercial == null || commercial.lines.isEmpty()) {
            commercialStage.status = WorkflowStageState.Status.READY;
            commercialStage.progress = 0;
        } else {
            commercial.recalc();
            int priced=0,needPrice=0,purchaseReady=0,purchaseTotal=0;
            for(CommercialLine l:commercial.lines){
                if(!l.includeInEstimate)continue;
                boolean needsPrice=l.category==CommercialLine.Category.LABOR || l.owner==CommercialLine.ProcurementOwner.MASTER;
                if(needsPrice){needPrice++;if(l.unitCost>0)priced++;}
                if(l.category==CommercialLine.Category.MATERIAL && l.owner==CommercialLine.ProcurementOwner.MASTER && l.purchaseQty>0){purchaseTotal++;if(l.received)purchaseReady++;}
            }
            int p=25;
            if(needPrice>0)p+=(int)Math.round(45.0*priced/needPrice);
            if(purchaseTotal==0)p+=30;else p+=(int)Math.round(30.0*purchaseReady/purchaseTotal);
            commercialStage.progress=Math.min(100,p);
            commercialStage.status=p>=90?WorkflowStageState.Status.COMPLETE:WorkflowStageState.Status.IN_PROGRESS;
            if(priced<needPrice)commercialStage.warnings.add("Не задані ціни/ставки: "+(needPrice-priced)+".");
            if(purchaseReady<purchaseTotal)commercialStage.warnings.add("Ще не отримано позицій закупівлі: "+(purchaseTotal-purchaseReady)+".");
        }
        out.stages.add(commercialStage);

        WorkflowStageState install = state(ProjectWorkflowStage.INSTALLATION);
        install.primaryAction = "Монтажна карта";
        if (!materials.complete()) block(install, "Спочатку підготуй кабельну карту і захист.");
        else if (!commercialStage.complete()) install.warnings.add("Кошторис/закупівля ще не завершені — монтаж можна вести, але контроль матеріалів неповний.");
        else if (cuts == null || cuts.pieces.isEmpty()) {
            install.status = WorkflowStageState.Status.READY;
        } else {
            int done = 0, labels = 0, cutDone = 0;
            for (CableCutPiece p : cuts.pieces) {
                if (p.cutDone) cutDone++;
                if (p.labelsPrinted) labels++;
                if (p.installed) done++;
            }
            int n = cuts.pieces.size();
            int progress = (int)Math.round((cutDone + labels + done) * 100.0 / (n * 3.0));
            install.progress = progress;
            install.status = done == n ? WorkflowStageState.Status.COMPLETE : WorkflowStageState.Status.IN_PROGRESS;
            if (cutDone < n) install.warnings.add("Ще не нарізано: " + (n-cutDone) + ".");
            if (labels < n) install.warnings.add("Без надрукованих етикеток: " + (n-labels) + ".");
            if (done < n) install.warnings.add("Не змонтовано: " + (n-done) + ".");
        }
        out.stages.add(install);

        WorkflowStageState quality = state(ProjectWorkflowStage.QUALITY);
        quality.primaryAction = "Quality Hub";
        if (!electrical.complete()) block(quality, "Потрібен завершений електроплан.");
        else {
            int p = 0;
            if (scene != null && scene.precisionReport != null) {
                p += Math.min(30, scene.precisionReport.score * 30 / 100);
                if (scene.precisionReport.score < 60) quality.warnings.add("Низька впевненість геометрії.");
            } else quality.warnings.add("Точність ще не перевірена контрольними замірами.");

            ElectricalDesignReport qa = new ElectricalDesignAnalyzer().analyze(scene==null?null:scene.apartmentPlan, floor);
            p += Math.min(25, qa.score * 25 / 100);
            if (qa.score < 75) quality.warnings.add("Електроплан має незавершені QA-пункти.");

            int verified = 0, pass = 0, fail = 0, circuits = field == null ? 0 : field.circuits.size();
            if (field != null) for (CircuitProtectionPlan c : field.circuits) {
                if (c.verifiedByElectrician) verified++;
                if ("PASS".equals(c.testStatus)) pass++;
                else if ("FAIL".equals(c.testStatus)) fail++;
            }
            if (circuits > 0) {
                p += (int)Math.round(20.0 * verified / circuits);
                p += (int)Math.round(25.0 * pass / circuits);
                if (verified < circuits) quality.warnings.add("Не всі групи захисту підтверджені.");
                if (pass < circuits) quality.warnings.add("Commissioning завершено не для всіх груп: "+pass+"/"+circuits+".");
                if (fail > 0) quality.blockers.add("Є групи зі статусом commissioning «Проблема»: "+fail+".");
            } else quality.warnings.add("Ще немає сформованих груп для commissioning.");
            quality.progress = Math.min(100, p);
            quality.status = quality.blockers.isEmpty() && p >= 80 ? WorkflowStageState.Status.COMPLETE : WorkflowStageState.Status.IN_PROGRESS;
        }
        out.stages.add(quality);

        WorkflowStageState asBuilt = state(ProjectWorkflowStage.AS_BUILT);
        asBuilt.primaryAction = "Фото / as-built";
        if (!install.complete()) block(asBuilt,"Спочатку заверши основний монтаж.");
        else {
            int count=evidence==null?0:evidence.items.size();int hidden=0,panelEvidence=0,finished=0,testEvidence=0;
            if(evidence!=null)for(EvidenceItem e:evidence.items){switch(e.category){case HIDDEN_WORK:hidden++;break;case PANEL:panelEvidence++;break;case FINISHED:finished++;break;case TEST:testEvidence++;break;default:break;}}
            int p=Math.min(40,count*10);if(hidden>0)p+=25;if(panelEvidence>0)p+=15;if(finished>0)p+=20;
            asBuilt.progress=Math.min(100,p);asBuilt.status=p>=70?WorkflowStageState.Status.COMPLETE:WorkflowStageState.Status.IN_PROGRESS;
            if(hidden==0)asBuilt.warnings.add("Немає фото прихованих робіт.");
            if(panelEvidence==0)asBuilt.warnings.add("Немає фото готового щита.");
            if(finished==0)asBuilt.warnings.add("Немає фото завершеного результату.");
        }
        out.stages.add(asBuilt);

        WorkflowStageState handover = state(ProjectWorkflowStage.HANDOVER);
        handover.primaryAction = "VIP здача";
        if (!install.complete()) handover.blockers.add("Монтаж ще не завершений.");
        if (!quality.complete()) handover.blockers.add("Перевірка/точність ще не завершені.");
        if (!asBuilt.complete()) handover.blockers.add("As-built фото/докази ще не завершені.");
        if (handover.blockers.isEmpty()) {
            boolean accepted=approval!=null&&approval.handoverAccepted;
            handover.status = accepted?WorkflowStageState.Status.COMPLETE:WorkflowStageState.Status.READY;
            handover.progress = accepted?100:95;
            if(approval==null||!approval.planApproved)handover.warnings.add("Немає зафіксованого погодження плану клієнтом.");
            if(approval==null||!approval.estimateApproved)handover.warnings.add("Немає зафіксованого погодження кошторису клієнтом.");
        } else {
            handover.status = WorkflowStageState.Status.BLOCKED;
            handover.progress = Math.min(90, (install.progress + quality.progress + asBuilt.progress) / 3);
        }
        out.stages.add(handover);

        int sum = 0;
        boolean nextSet = false;
        for (WorkflowStageState s : out.stages) {
            sum += s.progress;
            if (!nextSet && !s.complete()) {
                out.nextStage = s.stage;
                nextSet = true;
            }
        }
        out.overallProgress = out.stages.isEmpty() ? 0 : sum / out.stages.size();
        if (!nextSet) out.nextStage = ProjectWorkflowStage.HANDOVER;
        return out;
    }

    private WorkflowStageState state(ProjectWorkflowStage stage) {
        return new WorkflowStageState(stage);
    }

    private void block(WorkflowStageState s, String reason) {
        s.status = WorkflowStageState.Status.BLOCKED;
        s.progress = 0;
        s.blockers.add(reason);
    }
}
