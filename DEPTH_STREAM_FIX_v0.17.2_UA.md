# ElektorykScan v0.17.2 — Depth Stream Fix

## Симптом на Galaxy S22 Ultra
На екрані Smart Scan було видно TRACKING, ARCore planes і wall axis, але:

- `3D 0%`
- `RAW 0`
- `conf 0/255`
- `confirmed 0/0`
- лічильник `stale` швидко зростав приблизно до 300.

Це означало, що ARCore tracking працює, але застосунок сам відкидав Depth до реконструкції.

## Коренева причина
У v0.17.0/v0.17.1 новий Raw Depth приймався лише коли `rawDepth.getTimestamp() == frame.getTimestamp()`. На тестованому S22 Ultra ці timestamp не були bit-identical, тому практично кожне доступне Raw Depth зображення помилково позначалось stale. Та сама надто жорстка перевірка блокувала smoothed Depth fallback.

## Виправлення
1. Новий Raw Depth визначається зміною **його власного** timestamp відносно попереднього Raw Depth.
2. Повтор того самого Raw timestamp — це duplicate/reprojection і не інтегрується повторно.
3. Різниця між Frame timestamp і Raw Depth timestamp більше не є причиною відкидати геометрію; вона показується як діагностичний `Δts`.
4. Confidence image не відкидається лише через vendor timestamp metadata; перевірка реальної сумісності робиться через розмір і відповідний Raw Depth buffer.
5. Smoothed/full Depth теж дедуплікується за власним timestamp, а не вимагає рівності RGB Frame timestamp.
6. Якщо доступний `Config.DepthMode.AUTOMATIC`, він використовується замість `RAW_DEPTH_ONLY`: Raw Depth + confidence залишаються головним джерелом, а smoothed Depth стає страховкою для гладких/слабких поверхонь.
7. Збережені всі precision-фільтри v0.17.0: multi-view, baseline, 3D voxels, ARCore plane polygon, weighted room bounds, metric wall refinement, контрольні заміри.
8. Збережений install-compat `useLegacyPackaging true` із v0.17.1.

## Діагностика на екрані
Рядок тепер показує:

`RAW N • smooth N • conf ... • confirmed ... • dup ... • Δts ...`

Очікувана нормальна поведінка:
- `RAW` має почати зростати після руху телефоном;
- якщо Raw тимчасово слабкий, може рости `smooth`;
- `dup` може зростати між новими Raw Depth updates — це нормально;
- `Δts` може бути ненульовим і тепер не блокує скан;
- `confirmed` починає рости після повторного огляду тієї самої геометрії з іншої позиції.

## Regression test
Синтетичний тест відтворює ситуацію `Frame timestamp != Raw Depth timestamp`:
- перший новий Raw Depth приймається;
- повтор того самого Raw timestamp позначається duplicate;
- наступний новий Raw timestamp знову приймається.

Результат: `PASS raw=2 smooth=1 dup=1 vox=410`.
