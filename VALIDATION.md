# VALIDATION — ElektorykScan v0.17.0 Precision Fusion

- versionName: `0.17.0-precision-fusion`
- versionCode: `25`
- Java files: **200**
- Activity classes: **39**
- Manifest Activity entries: **39**
- Missing Activity registrations: **0**
- Manifest activities without source class: **0**
- Explicit Activity targets discovered: **38**
- Missing explicit target registrations: **0**
- Activity button heuristic: **180**, missing `setOnClickListener`: **0**
- XML files parsed: **8**, errors: **0**
- Whole-project Java parser-style pass: **NO syntax diagnostics** (Android/JSON symbols are expected to be unresolved without Android SDK)
- Scanner core compile with local Android/ARCore stubs: **PASS**
- SceneAnalysisPipeline precision overload compile with stubs: **PASS**
- Multi-view voxel precision regression: **PASS**
- ARCore plane polygon gate regression: **PASS**
- Synthetic room bounds + low-confidence false outer-wall regression: **PASS**
  - fixture target: `[-2.000, 2.500, -1.300, 1.400, -1.500, 2.000] m`
  - recovered: `[-1.990, 2.490, -1.289, 1.390, -1.489, 1.988] m`
- Synthetic rotated room axis 17°: **17.00° PASS**
- Wrong wall-yaw hint safety: **PASS** (`-30°`, confidence `1.0` did not override the strong 17° cloud)
- FineWallRefiner metric endpoint regression: **PASS**
  - synthetic truth about `0.035..3.925 m`
  - refined `0.053..3.901 m`, plane `z≈0.043 m`
- Plane polygon support regression: center `1.0`, near edge `0.615`, 5 cm outside `0.12`, far outside `0.0` — **PASS**

## Important limitation
These are static, compile-with-stubs and synthetic mathematical regressions. Physical ARCore camera/Depth accuracy, thermal behavior, device-specific camera configuration and real S22 Ultra tracking still require a built APK running on the phone. Synthetic centimeter-level results are **not** a promise of centimeter-level real-world accuracy.
