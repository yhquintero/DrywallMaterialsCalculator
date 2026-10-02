package com.drywall.calculator.utils

import com.drywall.common.utils.DatabaseBackupUtils
import org.junit.Assert.*
import org.junit.Test

class DatabaseBackupUtilsTest {
    @Test fun `valid paths are safe`() {
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe("database/drywall_db"))
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe("media/photos/img.jpg"))
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe("prefs/config.xml"))
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe("external_media/docs/report.pdf"))
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe("a/b/c/d/e/f"))
    }
    @Test fun `paths with double dot are unsafe`() {
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("../etc/passwd"))
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("media/../../etc/passwd"))
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("a/../../../b"))
    }
    @Test fun `paths with double dot in middle are unsafe`() {
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("media/../database/drywall_db"))
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("a/b/../c"))
    }
    @Test fun `absolute paths are unsafe`() {
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("/etc/passwd"))
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("/data/data/com.app/db"))
    }
    @Test fun `paths with backslash traversal are unsafe`() {
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("..\\windows\\system32"))
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("media\\..\\..\\etc"))
    }
    @Test fun `paths containing only dots are unsafe`() {
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("..."))
        assertFalse(DatabaseBackupUtils.isPathTraversalSafe("a/.../b"))
    }
    @Test fun `empty string is safe`() {
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe(""))
    }
    @Test fun `normal file name is safe`() {
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe("file.txt"))
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe("my_database.db-wal"))
        assertTrue(DatabaseBackupUtils.isPathTraversalSafe("data_2024.sqlite"))
    }
}