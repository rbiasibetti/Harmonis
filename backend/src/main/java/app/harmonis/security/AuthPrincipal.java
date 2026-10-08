package app.harmonis.security;

/** Identidade autenticada extraida do JWT. */
public record AuthPrincipal(long userId, String tenant, boolean owner) {}
