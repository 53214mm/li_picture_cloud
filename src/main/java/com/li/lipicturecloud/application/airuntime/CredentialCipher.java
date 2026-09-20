package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.CredentialVault;
import com.li.lipicturecloud.domain.airuntime.EncryptedCredential;

/**
 * 凭据加解密端口。明文只在调用方内存中短暂存在，落库、日志与响应均只出现密文。
 */
public interface CredentialCipher {

    EncryptedCredential encrypt(String plaintext);

    String decrypt(CredentialVault credential);
}
