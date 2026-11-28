# ElektorykScan v0.29.1 — Bounded Semantic Finalization

## Реально виправлено
- Прибрано безлімітне очікування `Future.get()` у `IndoorSemanticSystem.flush()`.
- Фінальна реконструкція більше не може зависнути назавжди через завислий/аномально повільний TFLite inference.
- Додано 3.5-секундний deadline: нормальний останній inference встигає потрапити у сцену, але геометрія/чекпойнт мають пріоритет над одним semantic frame.
- При timeout queued barrier скасовується, semantic generation інвалідовується, а наступний snapshot у тій самій фіналізації вже не чекає ще 3.5 с. Новий accepted inference/reset автоматично очищає цей fast-fail стан.
- `InterruptedException` тепер відновлює interrupt flag потоку замість тихого поглинання.

## Чому це важливо
`processCaptured()` викликає `semanticSystem.entities()` і `records()` на єдиному reconstruction worker. Раніше обидва шляхи заходили в `flush()` з безстроковим `Future.get()`. Якщо native TFLite/vendor delegate зависав, завершення скану, збереження сцени та подальші задачі worker могли зависнути назавжди, хоча Depth-геометрія вже була готова.

## Сумісність
Формат `scene.json`, проєкти, моделі, геометричні пороги та UX-контракти не змінені.
