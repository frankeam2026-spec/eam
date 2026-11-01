package com.windasoft.eam.repository.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OrganizationReferenceRepositoryTest {
  @Test
  void checksAllExistingBusinessReferenceSourcesWithTenantBoundParameters() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(0);
    assertFalse(new OrganizationReferenceRepository(db).businessReferences(7L, 101L));
    verify(db, times(8)).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
  }

  @Test
  void stopsAfterFirstExistingReference() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(1);
    assertTrue(new OrganizationReferenceRepository(db).businessReferences(7L, 101L));
    verify(db, times(1)).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
  }
}
