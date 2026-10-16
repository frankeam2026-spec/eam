package com.windasoft.eam.common;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class VersionGuardTest {
  @Test
  void matchingVersionSucceeds() {
    assertDoesNotThrow(() -> VersionGuard.check(3, 3));
  }

  @Test
  void staleVersionConflicts() {
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> VersionGuard.check(4, 3))
            .getStatusCode()
            .value());
  }

  @Test
  void absentStoredVersionFailsClosed() {
    assertThrows(ResponseStatusException.class, () -> VersionGuard.check(null, 0));
  }

  @Test
  void negativeVersionIsInvalid() {
    assertThrows(IllegalArgumentException.class, () -> VersionGuard.check(0, -1));
  }

  @Test
  void lostConcurrentUpdateConflicts() {
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> VersionGuard.updated(0))
            .getStatusCode()
            .value());
  }

  @Test
  void oneUpdateSucceeds() {
    assertDoesNotThrow(() -> VersionGuard.updated(1));
  }
}
