package cn.superhuang.data.scalpel.business.dsh.security;
import java.util.UUID;
import java.time.Instant;
/** Verified claims projected by the existing Admin JWT converter. */
public record DshLoginIdentity(UUID userId, Instant expiresAt) {}
