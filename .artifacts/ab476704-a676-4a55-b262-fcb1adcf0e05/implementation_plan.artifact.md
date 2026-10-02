# Fix Room Data Integrity Error

The application is crashing with an `IllegalStateException` because the Room database schema has changed but the version number remains at 1. Additionally, there appears to be a version discrepancy: the device contains a database schema corresponding to version 7 (hash `c7a5f55a...`), while the current code expects version 1 (hash `c2f6d2...`).

## User Review Required

> [!IMPORTANT]
> This fix involves incrementing the database version to **8**. Since no migration path is defined from the previous versions to 8, Room will perform a **destructive migration**. This means **all existing local data in the database will be cleared** and the database will be recreated with the current schema.

If you need to preserve the data, please provide the migration logic or revert the code to the version that matches the existing database schema.

## Proposed Changes

### [app]

#### [MODIFY] [AppDatabase.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/data/local/AppDatabase.kt)
- Increment `version` from `1` to `8` in the `@Database` annotation.

## Verification Plan

### Manual Verification
1. Re-run the application.
2. The app should no longer crash at startup.
3. Verify that the database is correctly initialized (though empty).
