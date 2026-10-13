# Test database setup (estateflow_test)

Integration tests run against a dedicated schema whose name ends with `_test`. They truncate all
seven tables before each test, so **never** point them at `estateflow`.

```sql
CREATE DATABASE estateflow_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ef_test'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL ON estateflow_test.* TO 'ef_test'@'localhost';
```

Load the schema into the test database (the first five lines of schema.sql name the database):

```bash
sed '1,5s/estateflow/estateflow_test/' db/schema.sql | mysql -u root -p
```

Then copy `db/db-test.properties.example` to `db-test.properties` in the project root and fill it in.
Run: `mvn verify -Pintegration`. Without `db-test.properties` the integration tests are skipped, not failed.
