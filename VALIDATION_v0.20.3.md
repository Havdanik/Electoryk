# v0.20.3 validation

- Source base: v0.20.2 INTERIOR STUDIO EXPANDED.
- AIDE screenshot errors mapped to 3 calls using fractional values with Ui.dp(Context,int).
- Added Ui.dp(Context,float) returning rounded px.
- Verified both int and float overload resolution with javac micro-test.
- Searched all Ui.dp calls for remaining float/double argument sites.
- ZIP structure and patch integrity checked.
