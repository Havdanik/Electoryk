# STABILITY / RECOVERY

## Що робити, якщо програма закрилась під час скану
1. Відкрий той самий об'єкт.
2. Зайди в Smart Scan.
3. Якщо є recovery-checkpoint, з'явиться кнопка `Відновити незавершений скан`.
4. Програма повторно запустить reconstruction без повторного обходу квартири.

## Якщо файл проєкту пошкодився
`Етапи об'єкта → Стабільність / відновлення / діагностика`

Можна відновити `.bak` окремо для:
- project;
- scene;
- floorplan;
- CUT list.

## FULL snapshots
Diagnostics дозволяє створити FULL snapshot вручну.
Handover автоматично створює snapshot перед VIP report.
Import існуючого проєкту створює recovery package перед оновленням.

Snapshot restore виконується як НОВА КОПІЯ — поточний об'єкт не перезаписується.

## Stability log
У `Diagnostics` видно останні breadcrumbs і crash/error stack traces.
Журнал обмежений по розміру, щоб сам не роздував storage.
