package cn.superhuang.data.scalpel.business.dsh.service;

import cn.superhuang.data.scalpel.business.dsh.config.DshProperties;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
@Component
public class DshCredentialCipher {
    private final DshProperties properties;
    public DshCredentialCipher(DshProperties properties) { this.properties=properties; }
    public String encrypt(UUID owner, String value) {
        try {
            byte[] iv=new byte[12]; new SecureRandom().nextBytes(iv);
            byte[] encrypted=cipher(Cipher.ENCRYPT_MODE,owner,iv).doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] all=new byte[iv.length+encrypted.length]; System.arraycopy(iv,0,all,0,iv.length); System.arraycopy(encrypted,0,all,iv.length,encrypted.length);
            return "v1:"+Base64.getEncoder().encodeToString(all);
        } catch(Exception e) { throw unavailable(); }
    }
    public String decrypt(UUID owner, String value) {
        try {
            if(!value.startsWith("v1:")) throw new IllegalArgumentException();
            byte[] all=Base64.getDecoder().decode(value.substring(3));
            if(all.length<29) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE,owner,Arrays.copyOfRange(all,0,12)).doFinal(Arrays.copyOfRange(all,12,all.length)),StandardCharsets.UTF_8);
        } catch(Exception e) { throw unavailable(); }
    }
    private Cipher cipher(int mode,UUID owner,byte[] iv) throws Exception {
        byte[] key=Base64.getDecoder().decode(properties.getCredentialKey());
        if(key.length!=32) throw new IllegalArgumentException();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        cipher.updateAAD(("datascalpel-dsh-v1:"+owner).getBytes(StandardCharsets.UTF_8));return cipher;
    }
    private RuntimeException unavailable() { return DshProblems.error(503,"DSH_CREDENTIAL_UNAVAILABLE","无法读取助手托管凭据，请检查独立加密密钥配置。"); }
}
