# Implementation Plan - Fix Room Database Integrity Error

The application is currently crashing at startup with a `java.lang.IllegalStateException: Room cannot verify the data integrity`. This is caused by a mismatch between the database schema version defined in the code and the schema actually present on the device's storage.

## Analysis
- **Current Code Version**: 1 (Hash: `00db0e0e8980411e82fa309bc153b2d1`)
- **Device Database Schema**: Hash `c2f6d2989f93c31094a413629f44d4c4`, which corresponds to version 8 based on `app/schemas/.../8.json`.
- **Current Entities**: The code now contains 23 entities, while version 8 only contained 20. This indicates further schema changes have occurred without a version bump.

## User Review Required

> [!IMPORTANT]
> To fix the crash, I will increment the database version to **9**.
>
> **Data Loss Warning**: Since the app is configured with `fallbackToDestructiveMigration()`, incrementing the version will cause Room to **clear all existing local data** and recreate the database with the current schema.
>
> If you have important data that must be preserved, we would need to implement explicit Migration objects, which would require detailed knowledge of the changes made between version 8 and the current state.

## Proposed Changes

### Database Configuration

#### [MODIFY] [AppDatabase.kt](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/java/com/drywall/calculator/data/local/AppDatabase.kt)
- Update the `@Database` annotation to set `version = 9`.

## Verification Plan

### Automated Tests
- None required for this simple configuration change, but a build will verify the new schema can be generated.

### Manual Verification
1. Deploy the app to the device.
2. Verify that the `FATAL EXCEPTION: DefaultDispatcher-worker-2` no longer occurs at startup.
3. Verify that the app opens the main screen correctly.
