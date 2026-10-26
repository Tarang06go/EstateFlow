# Third-party notices — EstateFlow 1.1.0

The Windows app image redistributes the following runtime components. Versions are the resolved runtime dependencies from `pom.xml`; test-only dependencies are not packaged. The Java 25 runtime carries its own `runtime/legal` notices inside the app image.

| Component | Version | License stated by the resolved artifact | License text / project |
|:---|:---|:---|:---|
| OpenJFX (base, graphics, controls, FXML) | 25.0.4 | GPLv2 with Classpath Exception (GPLv2+CE) | [OpenJDK GPLv2+CE](https://openjdk.org/legal/gplv2+ce.html), [OpenJFX](https://openjfx.io/) |
| MySQL Connector/J | 26.7.0 | GNU GPL v2 with Universal FOSS Exception v1.0 | The bundled JAR contains `LICENSE`; see [Connector/J](https://dev.mysql.com/downloads/connector/j/) and [Universal FOSS Exception](https://oss.oracle.com/licenses/universal-foss-exception/) |
| Logback Classic/Core | 1.5.32 | Eclipse Public License 2.0 and GNU LGPL 2.1 | [EPL 2.0](https://www.eclipse.org/legal/epl-2.0/), [LGPL 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html), [Logback](https://logback.qos.ch/) |
| SLF4J API | 2.0.17 | MIT License | The bundled JAR contains `META-INF/LICENSE.txt`; [SLF4J](https://www.slf4j.org/) |
| Protocol Buffers Java runtime | 4.31.1 | BSD 3-Clause | [BSD 3-Clause text](https://opensource.org/license/bsd-3-clause), [Protocol Buffers](https://github.com/protocolbuffers/protobuf) |
| Inter font (Regular, Bold) | bundled resource | SIL Open Font License 1.1 | Full text is in `LICENSES/Inter-OFL.txt` in the release |

Retain the app image's `runtime/legal` directory and these notices when redistributing the release. Refer to each artifact's accompanying license for the exact terms. No source-code changes to these components are included.
