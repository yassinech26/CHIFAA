package com.chifaa.backend.compatibility;
import java.util.UUID;
public interface AuditService { void record(UUID actor, String action, String resource); }
