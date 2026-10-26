package com.estateflow.service.biometric;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/** AES-256-GCM with fresh nonce and account/device/version-bound associated data. */
public final class AesGcmFaceTemplateCipher {
    private static final int TEMPLATE_VERSION = 1, KEY_VERSION = 1;
    private final TemplateKeyProvider keys;
    private final SecureRandom random = new SecureRandom();
    public AesGcmFaceTemplateCipher(TemplateKeyProvider keys) { this.keys = keys; }

    public EncryptedFaceTemplate encrypt(long userId, FaceEmbedding embedding) {
        byte[] key=keys.createOrLoadKey(userId), plaintext=encode(embedding), nonce=new byte[12]; random.nextBytes(nonce);
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(userId,keys.deviceId(),TEMPLATE_VERSION,KEY_VERSION,FaceModelFiles.SFACE_VERSION));
            byte[] encrypted=cipher.doFinal(plaintext);
            return new EncryptedFaceTemplate(KEY_VERSION,TEMPLATE_VERSION,FaceModelFiles.SFACE_VERSION,nonce,encrypted);
        } catch(Exception e) { throw new IllegalStateException("Face template encryption failed.",e); }
        finally { Arrays.fill(key,(byte)0); Arrays.fill(plaintext,(byte)0); }
    }

    public FaceEmbedding decrypt(long userId, EncryptedFaceTemplate template) {
        if (template.keyVersion()!=KEY_VERSION || template.templateVersion()!=TEMPLATE_VERSION || !FaceModelFiles.SFACE_VERSION.equals(template.modelVersion()))
            throw new IllegalStateException("Stored face template version is unsupported; enroll again.");
        byte[] key=keys.loadExistingKey(userId).orElseThrow(()->new IllegalStateException("The local biometric key is missing; enroll again."));
        byte[] nonce=template.nonce(), encrypted=template.ciphertext(), plaintext=null;
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(userId,keys.deviceId(),template.templateVersion(),template.keyVersion(),template.modelVersion()));
            plaintext=cipher.doFinal(encrypted);
            return decode(plaintext);
        } catch(Exception e) { throw new IllegalStateException("Stored face template cannot be verified; re-enrollment is required.",e); }
        finally { Arrays.fill(key,(byte)0); Arrays.fill(nonce,(byte)0); Arrays.fill(encrypted,(byte)0); if(plaintext!=null)Arrays.fill(plaintext,(byte)0); }
    }

    private static byte[] encode(FaceEmbedding embedding) {
        float[] vector=embedding.copyValues(); ByteArrayOutputStream bytes=new ByteArrayOutputStream(512);
        try(DataOutputStream out=new DataOutputStream(bytes)) { out.writeInt(vector.length); for(float value:vector)out.writeFloat(value); }
        catch(Exception e) { throw new IllegalStateException(e); } finally { Arrays.fill(vector,0f); }
        return bytes.toByteArray();
    }
    private static FaceEmbedding decode(byte[] bytes) throws Exception {
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if(in.readInt()!=128 || bytes.length!=4+128*4) throw new IllegalArgumentException("Invalid biometric feature encoding.");
            float[] vector=new float[128]; for(int i=0;i<vector.length;i++)vector[i]=in.readFloat();
            try { return new FaceEmbedding(vector); } finally { Arrays.fill(vector,0f); }
        }
    }
    private static byte[] aad(long userId,String deviceId,int templateVersion,int keyVersion,String modelVersion) {
        return ("EstateFlow-face|user="+userId+"|device="+deviceId+"|template="+templateVersion+"|key="+keyVersion+"|model="+modelVersion).getBytes(StandardCharsets.UTF_8);
    }
}
