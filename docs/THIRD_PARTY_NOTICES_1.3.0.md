# Third-party notices — EstateFlow 1.3.0

The Windows app image includes runtime dependencies resolved from `pom.xml`. Test-only dependencies are not packaged. The bundled Java runtime includes its notices under `runtime/legal`.

| Component | Version | License / notice |
|---|---:|---|
| OpenJFX (base, graphics, controls, FXML) | 25.0.4 | GPLv2 with Classpath Exception; OpenJFX project notices |
| MySQL Connector/J | 26.7.0 | GNU GPL v2 with Universal FOSS Exception; the bundled JAR contains its license |
| SLF4J API | 2.0.17 | MIT; the bundled JAR contains `META-INF/LICENSE.txt` |
| Logback Classic/Core | 1.5.32 | EPL 2.0 and LGPL 2.1 |
| JavaCPP and OpenCV Java bindings/native runtime | 1.5.14 / 4.14.0 | Apache License 2.0; retain applicable artifact notices |
| OpenBLAS JavaCPP runtime | 0.3.34-1.5.14 | BSD-style license; retain applicable artifact notices |
| Protocol Buffers Java runtime | 4.31.1 | BSD 3-Clause |
| Inter font (Regular, Bold) | bundled resource | SIL Open Font License 1.1; full text is in `LICENSES/Inter-OFL.txt` |
| YuNet and SFace ONNX models | downloaded separately | Model provenance/license notices are linked by `scripts/download-face-models.ps1`; these model files are not in the app image |

Retain this notice, `LICENSES`, the app image `runtime/legal` folder, and the license files shipped inside dependency JARs when redistributing. Verify the resolved runtime dependency set and corresponding license obligations before each public distribution.
