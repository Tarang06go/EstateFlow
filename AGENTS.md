# EstateFlow — Permanent Agent Instructions

## 1. Authority and Architecture
- `docs/4.EstateFlow_Architecture.pdf` is the authoritative architecture baseline v1.0 (Revision 4, FINAL).
- Technology stack: Java 25 (LTS), JavaFX 25, MySQL 8.4 LTS, Plain JDBC, Maven, JUnit 5, Mockito, JaCoCo, SLF4J, Logback.
- Strictly prohibited: Spring / Spring Boot, Hibernate / JPA, H2 / SQLite substitution for integration tests, Gradle, Lombok, Flyway/Liquibase, Guice.
- Database schema: 7 tables (`owner`, `tenant`, `property`, `rental_agreement`, `rent_charge`, `payment`, `maintenance_request`). Never alter schema, table names, columns, or constraints without explicit architectural authorization.
- `effective_end_date` in `rental_agreement` is a MySQL STORED generated column: `COALESCE(termination_date, end_date)`. Never write to it directly.

## 2. Layering and Dependency Rules
- **Controller** -> depends on Services, Models, DTOs, Exceptions, UI Utils. Never depends on DAO, JDBC, or SQL.
- **Service** -> depends on DAO interfaces, TransactionManager, Models, DTOs, Exceptions, Clock. Never depends on JavaFX, `java.sql.*`, or JDBC DAO implementations.
- **DAO Interfaces** -> define persistence contracts.
- **JDBC DAOs** -> implement DAO interfaces, extend `BaseJdbcDao`, handle SQL and ResultSet mapping. Never log exceptions, never contain business rules.
- **Model** -> Plain POJOs. Money is `BigDecimal` (scale 2, `HALF_UP`). Dates are `java.time.LocalDate` / `LocalDateTime`. No JavaFX properties, no JDBC types.
- **AppContext** -> Pure manual composition root. Constructor injection everywhere. No singletons.

## 3. Database Safety
- Development database: `estateflow`
- Integration test database: `estateflow_test`
- Destructive tests MUST NEVER target `estateflow`.
- Integration tests must verify that the target database schema name ends with `_test` before any cleanup/truncation.
- Never commit database credentials. `db.properties` and `db-test.properties` must stay in `.gitignore`.

## 4. Exception Handling and Logging
- Checked exceptions: `EstateFlowException` and subtypes (`ValidationException`, `BusinessRuleException`, `EntityNotFoundException`, `DuplicateEntityException`, `ReportExportException`). Safe to show to user.
- Unchecked exceptions: `DataAccessException` (with `SQLException` cause), `DuplicateKeyException`, `ConfigurationException`.
- Single logging point: `log.error(...)` is ONLY permitted in `ErrorHandler` and `EstateFlowApp` startup path. DAOs, services, and transaction managers do NOT log exceptions.
- Never swallow exceptions; always preserve the full cause chain. Never call `printStackTrace()`.

## 5. UI and Concurrency
- All database operations from JavaFX controllers must execute off the JavaFX Application Thread via `UiTask`.
- UI updates must occur on the JavaFX Application Thread.
