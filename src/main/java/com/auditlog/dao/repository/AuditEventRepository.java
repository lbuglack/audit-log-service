package com.auditlog.dao.repository;

import com.auditlog.dao.entity.AuditEventEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository
        extends JpaRepository<AuditEventEntity, UUID>, JpaSpecificationExecutor<AuditEventEntity> {}
