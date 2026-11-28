package com.elektoryk.scan.reconstruction;

import com.elektoryk.scan.model.AreaCheckResult;
import com.elektoryk.scan.model.ElectricalProject;
import com.elektoryk.scan.model.FloorPlan;
import com.elektoryk.scan.model.ProjectProfile;
import com.elektoryk.scan.model.RoomBounds;
import com.elektoryk.scan.model.RoomRect;
import com.elektoryk.scan.model.SceneSnapshot;

public class AreaReconciliationEngine {

    public AreaCheckResult check(ElectricalProject project, SceneSnapshot scene, FloorPlan plan) {
        AreaCheckResult r = new AreaCheckResult();
        if (project == null || project.approximateAreaM2 <= 0
                || (project.profile.areaReference == ProjectProfile.AreaReference.UNKNOWN
                || project.profile.areaAccuracy == ProjectProfile.AreaAccuracy.UNKNOWN)) {
            r.status = AreaCheckResult.Status.NO_REFERENCE;
            r.message = "Контрольну площу не задано.";
            r.scannedAreaM2 = areaFrom(plan, scene);
            return r;
        }

        r.referenceAreaM2 = project.approximateAreaM2;
        r.scannedAreaM2 = areaFrom(plan, scene);
        r.tolerancePercent = Math.max(1.0, project.profile.areaTolerancePercent);
        r.deltaM2 = r.scannedAreaM2 - r.referenceAreaM2;
        r.deltaPercent = r.referenceAreaM2 <= 0 ? 0 : (r.deltaM2 / r.referenceAreaM2) * 100.0;
        r.likelyMissingAreaM2 = Math.max(0.0, r.referenceAreaM2 - r.scannedAreaM2);

        double abs = Math.abs(r.deltaPercent);
        if (r.scannedAreaM2 <= 0.1) {
            r.status = AreaCheckResult.Status.RESCAN_REQUIRED;
            r.message = "Скан не дав придатної площі.";
        } else if (abs <= r.tolerancePercent) {
            r.status = AreaCheckResult.Status.MATCH;
            r.message = "Площа сходиться в межах допуску.";
        } else if (abs <= r.tolerancePercent * 1.8) {
            r.status = AreaCheckResult.Status.REVIEW;
            r.message = r.deltaM2 < 0
                    ? "Ймовірно втрачена частина площі. Перевір непроскановані зони/кімнати."
                    : "Сканована площа більша за контрольну. Перевір тип площі та межі кімнат.";
        } else {
            r.status = AreaCheckResult.Status.RESCAN_REQUIRED;
            r.message = r.deltaM2 < 0
                    ? "Розбіжність завелика: скан, імовірно, пропустив кімнату або значну зону."
                    : "Розбіжність завелика: межі скану або контрольна площа потребують перевірки.";
        }

        if (project.profile.areaReference == ProjectProfile.AreaReference.DOCUMENT_TOTAL
                && r.status == AreaCheckResult.Status.RESCAN_REQUIRED && abs < 25.0) {
            r.status = AreaCheckResult.Status.REVIEW;
            r.message = "Площа за документами може включати товщину стін/інші зони. Перевір склад площі перед перескануванням.";
        }
        return r;
    }

    private double areaFrom(FloorPlan plan, SceneSnapshot scene) {
        // Multi-room scan has priority: it represents the actually observed floor footprint
        // of the whole apartment, not a rectangular room envelope.
        if (scene != null && scene.apartmentPlan != null
                && scene.apartmentPlan.totalObservedAreaM2 > 0.5) {
            return scene.apartmentPlan.totalObservedAreaM2;
        }
        if (plan != null && !plan.rooms.isEmpty()) {
            double sum = 0;
            for (RoomRect room : plan.rooms) sum += Math.max(0, room.widthM * room.lengthM);
            if (sum > 0.1) return sum * plan.scaleCorrection * plan.scaleCorrection;
        }
        if (scene != null && scene.observedFloorAreaM2 > 0.5) return scene.observedFloorAreaM2;
        RoomBounds b = scene == null ? null : scene.roomBounds;
        if (b != null && b.isUsable()) return b.widthM() * b.lengthM();
        return 0.0;
    }
}
