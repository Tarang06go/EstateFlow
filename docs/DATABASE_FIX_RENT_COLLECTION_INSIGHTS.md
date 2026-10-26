# Database fix — Rent Collection Insights

## Confirmed failure

The packaged application's active log is `%USERPROFILE%\.estateflow\logs\estateflow.log`, as configured by `src/main/resources/logback.xml`. The newest reproduced failures (2026-10-26 04:01–04:12 local time) recorded `summarize late rent payment behavior failed [SQLState=S1009, code=0]`. The preserved cause and stack trace identify `JdbcRentCollectionInsightsDao.paymentBehavior` and MySQL Connector/J's `ClientPreparedStatement.checkBounds`: `Parameter index out of range (3 > number of parameters, which is 2)`.

This is a JDBC bind-count defect, not a stopped MySQL server, wrong release configuration, missing database, missing table/column, SQL syntax error, or business-calculation problem. The `paidOff` expression is correlated to `rc` and has no placeholders. The query contains only `BETWEEN ? AND ?` (two bind positions) when no property is selected, or one more property predicate when filtered. The DAO incorrectly bound a second copy of `from`, then `to`, and then a property id at position 4. Connector/J therefore rejected the query before sending it to MySQL. The controller's Refresh action reaches this query through `UiTask` → `RentCollectionInsightsService.report` → `JdbcRentCollectionInsightsDao.paymentBehavior`.

## Correction

`paymentBehavior` now binds `from` at index 1 and `to` at index 2, and binds `propertyId` at index 3 only when a property filter adds that SQL placeholder. The SQL and financial calculations are unchanged. A DAO unit regression test simulates Connector/J's placeholder bounds and checks both filtered and unfiltered calls.

`ErrorHandler` continues logging the original `DataAccessException` and full cause stack trace at the central error boundary. The popup no longer incorrectly suggests MySQL must be down; it includes the safe operation name, SQLState, and vendor code from `DataAccessException`, while excluding raw SQL, credentials, and parameter data. The log target is under the current user's `.estateflow\logs` directory, not the project `logs` folder.

## Database checks and limits

The development connection was read-only for diagnosis. MySQL returned schema `estateflow` and version `8.4.11`; metadata confirmed the analytics queries' referenced columns exist in `rent_charge`, `payment`, `rental_agreement`, `property`, and `tenant`. No business rows were selected or changed. The isolated `estateflow_test` connection also returned MySQL 8.4.11, but that schema currently contains no tables. No schema or migration was applied automatically. Therefore MySQL-backed integration queries still require the owner to provision the test schema using the documented manual database setup and migration process before `mvn verify -Pintegration` can execute them.

The source log under the project is an older development log. For this report, the actual per-user log from the running/package configuration was inspected; it contained the fresh reproduced exception. Historical missing-class and `NoSuchMethodError` messages were not the cause of this database popup.
