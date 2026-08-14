package com.li.lipicturecloud.domain.airuntime;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一个用户或平台连接某个模型供应商所需的协议、端点、凭据引用与启用状态。
 *
 * <p>端点只允许 HTTPS；第一阶段由平台白名单校验主机，防止 SSRF 与明文凭据外发。</p>
 */
public record ModelConnection(
        Long id,
        long subjectId,
        ModelProvider provider,
        String displayName,
        URI endpointUri,
        String modelCode,
        Long credentialId,
        boolean enabled,
        long revision) {

    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _\\-]{1,64}");
    private static final Pattern MODEL_CODE = Pattern.compile("[a-zA-Z0-9._\\-]{1,64}");

    public ModelConnection {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (subjectId <= 0 || revision < 0) {
            throw new IllegalArgumentException("invalid connection identity or revision");
        }
        Objects.requireNonNull(provider, "provider");
        if (displayName == null || !NAME.matcher(displayName.strip()).matches()) {
            throw new IllegalArgumentException("displayName must be 1-64 safe characters");
        }
        displayName = displayName.strip();
        Objects.requireNonNull(endpointUri, "endpointUri");
        if (!"https".equalsIgnoreCase(endpointUri.getScheme())) {
            throw new IllegalArgumentException("endpointUri must use https");
        }
        if (endpointUri.getHost() == null || endpointUri.getRawUserInfo() != null
                || endpointUri.getRawQuery() != null || endpointUri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "endpointUri must not contain userinfo, query or fragment");
        }
        if (modelCode == null || !MODEL_CODE.matcher(modelCode).matches()) {
            throw new IllegalArgumentException("modelCode must match " + MODEL_CODE.pattern());
        }
        if (credentialId != null && credentialId <= 0) {
            throw new IllegalArgumentException("credentialId must be positive or null");
        }
    }

    public static ModelConnection create(long subjectId, ModelProvider provider, String displayName,
                                         URI endpointUri, String modelCode, Long credentialId) {
        return new ModelConnection(null, subjectId, provider, displayName, endpointUri,
                modelCode, credentialId, false, 0L);
    }

    public static ModelConnection restore(Long id, long subjectId, ModelProvider provider,
                                          String displayName, URI endpointUri, String modelCode,
                                          Long credentialId, boolean enabled, long revision) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("persisted id must be positive");
        }
        return new ModelConnection(id, subjectId, provider, displayName, endpointUri,
                modelCode, credentialId, enabled, revision);
    }

    public ModelConnection withId(long persistedId) {
        if (persistedId <= 0 || id != null) {
            throw new IllegalStateException("invalid persisted id transition");
        }
        return new ModelConnection(persistedId, subjectId, provider, displayName, endpointUri,
                modelCode, credentialId, enabled, revision);
    }

    public ModelConnection enable() {
        if (enabled) {
            return this;
        }
        return new ModelConnection(id, subjectId, provider, displayName, endpointUri,
                modelCode, credentialId, true, Math.addExact(revision, 1L));
    }

    public ModelConnection disable() {
        if (!enabled) {
            return this;
        }
        return new ModelConnection(id, subjectId, provider, displayName, endpointUri,
                modelCode, credentialId, false, Math.addExact(revision, 1L));
    }

    public ModelConnection rotateCredential(long nextCredentialId) {
        if (nextCredentialId <= 0) {
            throw new IllegalArgumentException("nextCredentialId must be positive");
        }
        return new ModelConnection(id, subjectId, provider, displayName, endpointUri,
                modelCode, nextCredentialId, enabled, Math.addExact(revision, 1L));
    }
}
