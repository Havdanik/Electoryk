# ElektorykScan 0.24.7 — Stable Semantic Object Identity

- Scanner-promoted editor objects now persist `sourceSemanticId`, tying each editable 3D item to its persistent multi-view SemanticEntity track.
- Removed the unsafe 38 cm type/proximity de-duplication for new semantic objects; two nearby chairs/nightstands can now both survive promotion.
- Repeated promotion updates the same auto-created object instead of creating/suppressing objects by distance. Locked editor objects are respected and are not overwritten by later scanner refinement.
- Backward compatibility: legacy `SEMANTIC_*` furniture without source IDs gets a narrow 22 cm migration de-dup guard.
- Semantic taxonomy now promotes CHAIR/STOOL -> CHAIR, DESK -> DESK, FRIDGE/WASHER/OVEN, RADIATOR, MIRROR and SHELF/BOOKCASE to their dedicated editor types instead of collapsing most of them into CUSTOM/APPLIANCE.
- JSON is backward compatible: missing `sourceSemanticId` reads as empty.
- versionCode 88 / versionName 0.24.7-stable-semantic-object-identity.
