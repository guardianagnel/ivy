/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.plugins.signer.bouncycastle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.SignatureException;
import java.util.Iterator;

import org.apache.ivy.plugins.signer.SignatureGenerator;
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.bcpg.BCPGOutputStream;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.PGPUtil;

public class OpenPGPSignatureGenerator implements SignatureGenerator {
    
    private static final long MASK = 0xFFFFFFFFL;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private String name;
    private String secring;
    private String password;
    private String keyId;    
    
    private PGPSecretKey pgpSec;
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getExtension() {
        return "asc";
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public void setSecring(String secring) {
        this.secring = secring;
    }
    
    public void setKeyId(String keyId) {
        if (!"auto".equals(keyId)) {
            this.keyId = keyId;
        }
    }
    
    public void sign(File src, File dest) throws IOException {
        OutputStream out = null;
        InputStream in = null;
        InputStream keyIn = null;
        
        try {
            if (secring == null) {
                secring = System.getProperty("user.home") + "/.gnupg/secring.gpg";
            }
            
            if (pgpSec == null) {
                keyIn = new FileInputStream(secring);
                pgpSec = readSecretKey(keyIn);
            }
            
            PGPPrivateKey pgpPrivKey = pgpSec.extractPrivateKey(password.toCharArray(), BouncyCastleProvider.PROVIDER_NAME);        
            PGPSignatureGenerator sGen = new PGPSignatureGenerator(pgpSec.getPublicKey().getAlgorithm(), PGPUtil.SHA1, BouncyCastleProvider.PROVIDER_NAME);
            sGen.initSign(PGPSignature.BINARY_DOCUMENT, pgpPrivKey);
    
            in = new FileInputStream(src);
            out = new BCPGOutputStream(new ArmoredOutputStream(new FileOutputStream(dest)));
            
            int ch = 0;
            while ((ch = in.read()) >= 0) {
                sGen.update((byte) ch);
            }
            
            sGen.generate().encode(out);
        } catch (SignatureException e) {
            IOException ioexc = new IOException();
            ioexc.initCause(e);
            throw ioexc;
        } catch (PGPException e) {
            IOException ioexc = new IOException();
            ioexc.initCause(e);
            throw ioexc;
        } catch (NoSuchAlgorithmException e) {
            IOException ioexc = new IOException();
            ioexc.initCause(e);
            throw ioexc;
        } catch (NoSuchProviderException e) {
            IOException ioexc = new IOException();
            ioexc.initCause(e);
            throw ioexc;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {}
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {}
            }
            if (keyIn != null) {
                try {
                    keyIn.close();
                } catch (IOException e) {}
            }
        }
    }

    private PGPSecretKey readSecretKey(InputStream in) throws IOException, PGPException {
        in = PGPUtil.getDecoderStream(in);
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(in);

        PGPSecretKey key = null; 
        for (Iterator it = pgpSec.getKeyRings(); key == null && it.hasNext(); ) {
            PGPSecretKeyRing kRing = (PGPSecretKeyRing) it.next();   
            
            for (Iterator it2 = kRing.getSecretKeys(); key == null && it2.hasNext(); ) {
                PGPSecretKey k = (PGPSecretKey) it2.next();
                if ((keyId == null) && k.isSigningKey()) {
                    key = k;
                }
                if ((keyId != null) && (Long.valueOf(keyId, 16).longValue() == (k.getKeyID() & MASK))) {
                    key = k;
                }
            }
        }
        
        if (key == null) {
            throw new IllegalArgumentException("Can't find encryption key" + 
                (keyId != null ? " '" + keyId + "' " : " ") + "in key ring.");
        }
        
        return key;
    }

}
