package com.github.TejasLamba2006.AuthMeUI.conflict;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DialogConflictGuardTest {

    @Test
    void parsesPlainVersion() {
        assertEquals(6, DialogConflictGuard.parseMajorVersion("6.0.1"));
        assertEquals(5, DialogConflictGuard.parseMajorVersion("5.7.0"));
    }

    @Test
    void stripsSnapshotSuffix() {
        assertEquals(6, DialogConflictGuard.parseMajorVersion("6.0.1-SNAPSHOT"));
        assertEquals(5, DialogConflictGuard.parseMajorVersion("5.6.0-SNAPSHOT"));
    }

    @Test
    void stripsReleaseSuffix() {
        assertEquals(6, DialogConflictGuard.parseMajorVersion("6.0.0-R1"));
        assertEquals(6, DialogConflictGuard.parseMajorVersion("6.0.1 #123"));
    }

    @Test
    void handlesNullAndBlank() {
        assertNull(DialogConflictGuard.parseMajorVersion(null));
        assertNull(DialogConflictGuard.parseMajorVersion(""));
        assertNull(DialogConflictGuard.parseMajorVersion("   "));
    }

    @Test
    void rejectsNonNumeric() {
        assertNull(DialogConflictGuard.parseMajorVersion("unknown"));
    }
}
