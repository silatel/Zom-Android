package org.awesomeapp.messenger.crypto;

import org.awesomeapp.messenger.crypto.IOtrKeyManager;
import im.zom.messenger.R;

import org.awesomeapp.messenger.ImApp;
import org.awesomeapp.messenger.model.Address;

import org.awesomeapp.messenger.util.AES_256_CBC;
import org.awesomeapp.messenger.util.LogCleaner;
import org.awesomeapp.messenger.util.OpenSSLPBEInputStream;
import org.awesomeapp.messenger.util.OpenSSLPBEOutputStream;
import org.awesomeapp.messenger.util.Version;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPrivateKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

import net.java.otr4j.OtrKeyManager;
import net.java.otr4j.OtrKeyManagerListener;
import net.java.otr4j.OtrKeyManagerStore;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionID;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import info.guardianproject.iocipher.*;

public class OtrAndroidKeyManagerImpl extends IOtrKeyManager.Stub implements OtrKeyManager {

    private SimplePropertiesStore store;

    private OtrCryptoEngineImpl cryptoEngine;

    private final static String KEY_ALG = "DSA";
    private final static int KEY_SIZE = 1024;
    private final static Version CURRENT_VERSION = new Version("2.0.0");

    private static OtrAndroidKeyManagerImpl _instance;

   // private static final String FILE_KEYSTORE_ENCRYPTED = "otr_keystore.ofc";
    private static final String FILE_KEYSTORE_UNENCRYPTED = "otr_keystore.properties";

    private final static String STORE_ALGORITHM = "PBEWITHMD5AND256BITAES-CBC-OPENSSL";

    public static synchronized OtrAndroidKeyManagerImpl getInstance(Context context)
    {

        try
        {
            if (_instance == null) {
                File fKeyStore;

                fKeyStore = new info.guardianproject.iocipher.File("/", FILE_KEYSTORE_UNENCRYPTED);
                 _instance = new OtrAndroidKeyManagerImpl(fKeyStore);

            }

            return _instance;
        }
        catch (IOException ioe)
        {
            Toast.makeText(context, R.string.your_keystore_is_corrupted_please_re_install_chatsecure_or_clear_data_for_the_app, Toast.LENGTH_LONG).show();
            throw new RuntimeException("Could not open keystore",ioe);
        }
    }

    private OtrAndroidKeyManagerImpl(File filepath) throws IOException {

        store = new SimplePropertiesStore(filepath);

        cryptoEngine = new OtrCryptoEngineImpl();

    }

    /*
    private void upgradeStore() {

        LogCleaner.warn(ImApp.LOG_TAG, "upgrading keystore");

        String version = store.getPropertyString("version");

        if (version == null || new Version(version).compareTo(new Version("1.0.0")) < 0) {
            // Add verified=false entries for TOFU sync purposes
            Set<Object> keys = Sets.newHashSet(store.getKeySet());
            for (Object keyObject : keys) {
                String key = (String)keyObject;
                if (key.endsWith(".fingerprint")) {
                    String fullUserId = key.replaceAll(".fingerprint$", "");
                    String fingerprint = store.getPropertyString(key);
                    String verifiedKey = buildPublicKeyVerifiedId(fullUserId, fingerprint);
                    if (!store.hasProperty(verifiedKey)) {
                        // Avoid save
                        store.setProperty(verifiedKey, "false");
                    }
                }
            }

            // This will save
            store.setProperty("version", CURRENT_VERSION.toString());
        }


        File fileOldKeystore = new File(FILE_KEYSTORE_UNENCRYPTED);
        if (fileOldKeystore.exists())
        {
            LogCleaner.warn(ImApp.LOG_TAG, "upgrading unencrypted keystore");
            try {
                SimplePropertiesStore storeOldKeystore = new SimplePropertiesStore(fileOldKeystore);

                Enumeration<Object> enumKeys = storeOldKeystore.getKeys();

                while(enumKeys.hasMoreElements())
                {
                    String key = (String)enumKeys.nextElement();
                    LogCleaner.warn(ImApp.LOG_TAG, "importing key: " + key);
                    store.setProperty(key, storeOldKeystore.getPropertyString(key));

                }

                store.save();

                fileOldKeystore.delete();

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        else
        {
            LogCleaner.warn(ImApp.LOG_TAG, "unencrypted keystore not found");

        }

    }
    */

    static class SimplePropertiesStore implements OtrKeyManagerStore {

        private Properties mProperties = new Properties();
        private File mStoreFile;
        private String mPassword;

        public SimplePropertiesStore(File storeFile) throws IOException {
            mStoreFile = storeFile;

            if (storeFile.exists())
                mProperties.load(new FileInputStream(mStoreFile));
            

        }

        /**
        public SimplePropertiesStore(java.io.File storeFile, final String password, boolean isImportFromKeySync) throws IOException {

            OtrDebugLogger.log("Loading store from encrypted file");
            mStoreFile = storeFile;

            if (password == null)
                throw new IOException ("invalid password");

            mPassword = password;

            if (mStoreFile.exists())
                if (isImportFromKeySync)
                    loadAES(mPassword);
                else
                    loadOpenSSL(mPassword);
            
        }*/

        private void loadAES(final String password) throws IOException
        {
            String decoded;
                decoded = AES_256_CBC.decrypt(mStoreFile, password);
                mProperties.load(new ByteArrayInputStream(decoded.getBytes()));
        }

        public void setProperty(String id, String value) {
            mProperties.setProperty(id, value);
            persist();
            
        }


        public void setProperty(String id, boolean value) {
            mProperties.setProperty(id, Boolean.toString(value));
            persist();
            
        }
       
        public synchronized boolean persist ()
        {
        
            try {
                if (mPassword != null)
                    saveOpenSSL(mPassword, mStoreFile);
                else
                    savePlain(mStoreFile);

                
                return true;
            } catch (IOException e) {
                LogCleaner.error(ImApp.LOG_TAG, "error saving keystore", e);
                return false;
            }
        }


        public boolean export (String password, File storeFile)
        {
            try {
                saveOpenSSL (password, storeFile);
                return true;
            } catch (IOException e) {
                LogCleaner.error(ImApp.LOG_TAG, "error saving keystore", e);
                return false;
            }
        }

        private void saveOpenSSL (String password, File fileStore) throws IOException
        {
            // Encrypt these bytes
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try
            {
                OpenSSLPBEOutputStream encOS = new OpenSSLPBEOutputStream(baos, STORE_ALGORITHM, 1, password.toCharArray());
                mProperties.store(encOS, null);
                encOS.flush();
            }
            catch (IllegalArgumentException iae)
            {

                //might be a unicode character in the password
                OpenSSLPBEOutputStream encOS = new OpenSSLPBEOutputStream(baos, STORE_ALGORITHM, 1, Base64.encodeToString(password.getBytes(),Base64.NO_WRAP).toCharArray());
                mProperties.store(encOS, null);
                encOS.flush();


            }

            FileOutputStream fos = new FileOutputStream(fileStore);
            fos.write(baos.toByteArray());
            fos.flush();
            fos.close();

        }

        private void savePlain(File fileStore) throws IOException
        {
            FileOutputStream fos = new FileOutputStream(fileStore);
            mProperties.store(fos,"");

        }

        private void loadOpenSSL(String password) throws IOException
        {

            if (!mStoreFile.exists())
                return;

            if (mStoreFile.length() == 0)
                return;

            FileInputStream fis = null;
            OpenSSLPBEInputStream encIS = null;
            try {

                fis = new FileInputStream(mStoreFile);

                // Decrypt the bytes
                encIS = new OpenSSLPBEInputStream(fis, STORE_ALGORITHM, 1, password.toCharArray());
                mProperties.load(encIS);
            }
            catch (IllegalArgumentException iae)
            {
                //might be a unicode character in the password
                encIS = new OpenSSLPBEInputStream(fis, STORE_ALGORITHM, 1, (Base64.encodeToString(password.getBytes(),Base64.NO_WRAP)).toCharArray());
                mProperties.load(encIS);

            } catch (java.io.FileNotFoundException fnfe) {
                OtrDebugLogger.log("Properties store file not found: First time?");
                mStoreFile.getParentFile().mkdirs();
            } finally {
                encIS.close();
                fis.close();
            }
        }

        public void setProperty(String id, byte[] value) {
            mProperties.setProperty(id, (Base64.encodeToString(value,Base64.NO_WRAP)));
            persist();
            
        }


        public void removeProperty(String id) {
            mProperties.remove(id);
            persist();
            

        }

        public String getPropertyString(String id) {
            return mProperties.getProperty(id);
        }

        public byte[] getPropertyBytes(String id) {
            String value = mProperties.getProperty(id);

            if (value != null)
                return Base64.decode(value.getBytes(),Base64.NO_WRAP);
            return null;
        }

        public String getProperty (String id)
        {
            return mProperties.getProperty(id);
        }

        public boolean getPropertyBoolean(String id, boolean defaultValue) {
            try {
                return Boolean.valueOf(mProperties.get(id).toString());
            } catch (Exception e) {
                return defaultValue;
            }
        }

        public boolean hasProperty(String id) {
            return mProperties.containsKey(id);
        }

        public Enumeration<Object> getKeys ()
        {
            return mProperties.keys();
        }

        public Set<Object> getKeySet ()
        {
            return mProperties.keySet();
        }
    }

    private List<OtrKeyManagerListener> listeners = new Vector<OtrKeyManagerListener>();

    public void addListener(OtrKeyManagerListener l) {
        synchronized (listeners) {
            if (!listeners.contains(l))
                listeners.add(l);
        }
    }

    public void removeListener(OtrKeyManagerListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    public void generateLocalKeyPair(SessionID sessionID) {
        if (sessionID == null)
            return;

        generateLocalKeyPair(sessionID.getLocalUserId());
    }

    public void regenerateLocalPublicKey(KeyFactory factory, String fullUserId, DSAPrivateKey privKey) {

        String userId = Address.stripResource(fullUserId);

        BigInteger x = privKey.getX();
        DSAParams params = privKey.getParams();
        BigInteger y = params.getG().modPow(x, params.getP());
        DSAPublicKeySpec keySpec = new DSAPublicKeySpec(y, params.getP(), params.getQ(), params.getG());
        PublicKey pubKey;
        try {
            pubKey = factory.generatePublic(keySpec);
            storeLocalPublicKey(userId, pubKey);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public KeyPair generateLocalKeyPair() {

        OtrDebugLogger.log("generating local key pair");

        KeyPair keyPair;
        try {

            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALG);
            kpg.initialize(KEY_SIZE);
            keyPair = kpg.genKeyPair();
            OtrDebugLogger.log("SUCCESS! generating local key pair");
            return keyPair;
        } catch (NoSuchAlgorithmException e) {
            OtrDebugLogger.log("no such algorithm", e);
            return null;
        }

    }

    public void storeKeyPair (String userId, KeyPair keyPair)
    {

        // Store Private Key.
        PrivateKey privKey = keyPair.getPrivate();
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privKey.getEncoded());

        this.store.setProperty(userId + ".privateKey", pkcs8EncodedKeySpec.getEncoded());

        try
        {
            // Store Public Key.
            PublicKey pubKey = keyPair.getPublic();
            storeLocalPublicKey(userId, pubKey); //this will do saving

        }
        catch (Exception e)
        {
            throw new RuntimeException ("Error init local keypair");
        }
    }


    public void generateLocalKeyPair(String fullUserId) {

        String userId = Address.stripResource(fullUserId);

        OtrDebugLogger.log("generating local key pair for: " + userId);

        KeyPair keyPair;
        try {

            KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALG);
            kpg.initialize(KEY_SIZE);

            keyPair = kpg.genKeyPair();
        } catch (NoSuchAlgorithmException e) {
            OtrDebugLogger.log("no such algorithm", e);
            return;
        }

        OtrDebugLogger.log("SUCCESS! generating local key pair for: " + userId);
        storeKeyPair(userId, keyPair);

        /**
        // Store Private Key.
        PrivateKey privKey = keyPair.getPrivate();
        PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privKey.getEncoded());

        this.store.setProperty(userId + ".privateKey", pkcs8EncodedKeySpec.getEncoded());

        try
        {
            // Store Public Key.
            PublicKey pubKey = keyPair.getPublic();

        }
        catch (Exception e)
        {
            throw new RuntimeException ("Error init local keypair");
        }*/
    }

    public void storeLocalPublicKey(String fullUserId, PublicKey pubKey) throws OtrCryptoException {

        String userId = Address.stripResource(fullUserId);

        String fingerprintString = cryptoEngine.getFingerprint(pubKey);
        String fingerprintKey = userId + ".fingerprint";

        //check if we already have this
        if ((!store.hasProperty(fingerprintKey)) ||
                (!store.getProperty(fingerprintKey).equals(fingerprintString)))
        {
            X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(pubKey.getEncoded());
            this.store.setProperty(userId + ".publicKey", x509EncodedKeySpec.getEncoded());
            this.store.setProperty(fingerprintKey, fingerprintString);
     

        }
        
      //  Log.i(ImApp.LOG_TAG, "New public key generated: " + fingerprintString);

    }

    public boolean importKeyStore(String filePath, String password, boolean overWriteExisting, boolean deleteImportedFile) throws IOException
    {
        SimplePropertiesStore storeNew = null;

        File fileOtrKeystore = new File(filePath);

        if (fileOtrKeystore.getName().endsWith(".ofcaes")) {
            //TODO implement GUI to get password via QR Code, and handle wrong password
        //    storeNew = new SimplePropertiesStore(fileOtrKeystore, password, true);
            deleteImportedFile = true; // once its imported, its no longer needed
        }
        else
        {
            return false;
        }

        Enumeration<Object> enumKeys = storeNew.getKeys();


        String key;

        while (enumKeys.hasMoreElements())
        {
            key = (String)enumKeys.nextElement();

            boolean hasKey = store.hasProperty(key);

            if (!hasKey || overWriteExisting)
                store.setProperty(key, storeNew.getPropertyString(key));

        }

        if (deleteImportedFile)
            fileOtrKeystore.delete();

        return true;
    }

    public String getLocalFingerprint(SessionID sessionID) {
        return getLocalFingerprint(sessionID.getLocalUserId());
    }

    public String getLocalFingerprint(String fullUserId) {

        String userId = Address.stripResource(fullUserId);

        KeyPair keyPair = loadLocalKeyPair(userId);

        if (keyPair == null)
            return null;

        return getFingerprint(keyPair.getPublic());

    }

    public String getFingerprint (PublicKey pubKey)
    {
        try {
            String fingerprint = cryptoEngine.getFingerprint(pubKey);
            //  OtrDebugLogger.log("got fingerprint for: " + userId + "=" + fingerprint);
            return fingerprint;

        } catch (OtrCryptoException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getRemoteFingerprint(SessionID sessionID) {
        return getRemoteFingerprint(sessionID.getRemoteUserId());
    }

    public boolean hasRemoteFingerprint (String userId)
    {
        Enumeration<Object> keys = store.getKeys();

        while (keys.hasMoreElements())
        {
            String key = (String)keys.nextElement();

            if (key.startsWith(userId))
                return true;


        }

        return false;
    }

    public String getRemoteFingerprint(String fullUserId) {

        String fingerprint = this.store.getProperty(fullUserId + ".fingerprint");
        if (fingerprint != null) {
            // If we have a fingerprint stashed, assume it is correct.
            return fingerprint;
        }

        //if we can't find an exact match, let's show the first one that matches the id sans resource
        for (Object fpKey : store.getKeySet())
        {
            String fpKeyString = (String)fpKey;
            if (fpKeyString.startsWith(fullUserId) && fpKeyString.endsWith(".fingerprint")) {
                fingerprint = store.getProperty(fpKeyString);
                if (fingerprint != null)
                    return fingerprint;
            }
        }

        PublicKey remotePublicKey = loadRemotePublicKeyFromStore(fullUserId);
        if (remotePublicKey == null)
            return null;

        try {
            // Store the fingerprint, for posterity.
            String fingerprintString = new OtrCryptoEngineImpl().getFingerprint(remotePublicKey);
            this.store.setProperty(fullUserId + ".fingerprint", fingerprintString);
            
            return fingerprintString;
        } catch (OtrCryptoException e) {
            throw new RuntimeException("OtrCryptoException getting remote fingerprint",e);

        }
    }

    public String[] getRemoteFingerprints(String userId) {

        Enumeration<Object> keys = store.getKeys();

        ArrayList<String> results = new ArrayList<String>();

        String baseUserId = Address.stripResource(userId);

        while (keys.hasMoreElements())
        {
            String key = (String)keys.nextElement();

            if (key.startsWith(baseUserId + '/') && key.endsWith(".fingerprint"))
            {

                String fingerprint = this.store.getProperty(userId + ".fingerprint");
                if (fingerprint != null) {
                    // If we have a fingerprint stashed, assume it is correct.
                    results.add(fingerprint);
                }

            }

        }

        String[] resultsString = new String[results.size()];
        return results.toArray(resultsString);
    }

    public boolean isVerified(SessionID sessionID) {
        if (sessionID == null)
            return false;
        return isVerified(sessionID.getRemoteUserId());
    }

    public boolean isVerified(String remoteUserId) {
        if (remoteUserId == null)
            return false;

        String remoteFingerprint =getRemoteFingerprint(remoteUserId);

        if (remoteFingerprint != null)
        {
            String username = Address.stripResource(remoteUserId);
            String pubKeyVerifiedToken = buildPublicKeyVerifiedId(username, remoteFingerprint);
            return this.store.getPropertyBoolean(pubKeyVerifiedToken, false);
        }
        else
        {
            return false;
        }
    }

    public boolean isVerifiedUser(String fullUserId) {

        String userId = Address.stripResource(fullUserId);
        String remoteFingerprint = getRemoteFingerprint(fullUserId);

        if (remoteFingerprint != null)
        {
            String pubKeyVerifiedToken = buildPublicKeyVerifiedId(userId, remoteFingerprint);

            return this.store.getPropertyBoolean(pubKeyVerifiedToken, false);
        }
        else
            return false;
    }

    public KeyPair loadLocalKeyPair(SessionID sessionID) {
        if (sessionID == null)
            return null;

        return loadLocalKeyPair(sessionID.getLocalUserId());
    }

    private KeyPair loadLocalKeyPair(String fullUserId) {
        PublicKey publicKey;
        PrivateKey privateKey;

        String userId = Address.stripResource(fullUserId);


        try {
            // Load Private Key.

            byte[] b64PrivKey = this.store.getPropertyBytes(userId + ".privateKey");
            if (b64PrivKey == null)
                return null;

            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(b64PrivKey);

            // Generate KeyPair.
            KeyFactory keyFactory;
            keyFactory = KeyFactory.getInstance(KEY_ALG);
            privateKey = keyFactory.generatePrivate(privateKeySpec);

            // Load Public Key.
            byte[] b64PubKey = this.store.getPropertyBytes(userId + ".publicKey");
            if (b64PubKey == null)
                return null;

            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64PubKey);
            publicKey = keyFactory.generatePublic(publicKeySpec);
        } catch (Exception e) {
           throw new RuntimeException(e);
        }

        return new KeyPair(publicKey, privateKey);
    }

    public PublicKey loadRemotePublicKey(SessionID sessionID) {

        return loadRemotePublicKeyFromStore(sessionID.getRemoteUserId());
    }

    private PublicKey loadRemotePublicKeyFromStore(String fullUserId) {

      //  if (!Address.hasResource(fullUserId))
        //  return null;

        byte[] b64PubKey = this.store.getPropertyBytes(fullUserId + ".publicKey");
        if (b64PubKey == null) {
            return null;

        }

        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64PubKey);

        // Generate KeyPair from spec
        KeyFactory keyFactory;
        try {
            keyFactory = KeyFactory.getInstance(KEY_ALG);

            return keyFactory.generatePublic(publicKeySpec);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        } catch (InvalidKeySpecException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void savePublicKey(SessionID sessionID, PublicKey pubKey) {
        if (sessionID == null)
            return;

        X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(pubKey.getEncoded());

      //  if (!Address.hasResource(sessionID.getRemoteUserId()))
        //    return;

        String fullUserId = sessionID.getRemoteUserId();

        this.store.setProperty(fullUserId + ".publicKey", x509EncodedKeySpec.getEncoded());
        // Stash the associated fingerprint.  This saves calculating it in the future
        // and is useful for transferring rosters to other apps.
        try {
            String fingerprintString = new OtrCryptoEngineImpl().getFingerprint(pubKey);
            String verifiedToken = buildPublicKeyVerifiedId(sessionID.getRemoteUserId(), fingerprintString);
            String fingerprintKey = fullUserId + ".fingerprint";

            //if a fingerprint for this userid exists, then check if the key is verified
            if (this.store.hasProperty(fingerprintKey)) {
                if (!this.store.hasProperty(verifiedToken))
                    this.store.setProperty(verifiedToken, false);
            }
            else
            {
                //if there is no key, then we can "trust on first use"!
                this.store.setProperty(fingerprintKey, fingerprintString);
                this.store.setProperty(verifiedToken, true);
            }

            
        } catch (OtrCryptoException e) {
            Log.e(ImApp.LOG_TAG,"otr error: " + e.getMessage(),e);
        }
    }

    public void unverify(SessionID sessionID) {
        if (sessionID == null)
            return;

        if (!isVerified(sessionID))
            return;

        unverifyUser(sessionID.getRemoteUserId());

        for (OtrKeyManagerListener l : listeners)
            l.verificationStatusChanged(sessionID);

    }

    public void unverifyUser(String fullUserId) {

        if (!isVerifiedUser(fullUserId))
            return;

        store.setProperty(buildPublicKeyVerifiedId(fullUserId, getRemoteFingerprint(fullUserId)), false);
  

    }

    public void verify(SessionID sessionID) {
        if (sessionID == null)
            return;

        if (this.isVerified(sessionID))
            return;

        verifyUser(sessionID.getRemoteUserId());

    }

    public void remoteVerifiedUs(SessionID sessionID) {
        if (sessionID == null)
            return;

        for (OtrKeyManagerListener l : listeners)
            l.remoteVerifiedUs(sessionID);
    }

    private static String buildPublicKeyVerifiedId(String userId, String fingerprint) {
        if (fingerprint == null)
            return null;

        return (Address.stripResource(userId) + "." + fingerprint + ".publicKey.verified");
    }

    public void verifyUser(String userId) {
        if (userId == null)
            return;

        if (this.isVerifiedUser(userId))
            return;

        String verifiedKeyId = buildPublicKeyVerifiedId(userId, getRemoteFingerprint(userId));

        if (verifiedKeyId != null)
            this.store
                    .setProperty(verifiedKeyId, true);

    }

    public void verifyUser(String userId, String fingerprint) {
        if (userId == null)
            return;

        if (this.isVerifiedUser(userId))
            return;

        this.store
                .setProperty(buildPublicKeyVerifiedId(userId, fingerprint), true);
      }

    public boolean doKeyStoreExport (String password)
    {


        // if otr_keystore.ofcaes is in the SDCard root, import it
        File otrKeystoreAES = new File(Environment.getExternalStorageDirectory(),
                "otr_keystore.ofcaes");


        return store.export(password, otrKeystoreAES);
    }
    public static boolean checkForKeyImport (Intent intent, Activity activity)
    {
        boolean doKeyStoreImport = false;

        // if otr_keystore.ofcaes is in the SDCard root, import it
        File otrKeystoreAES = new File(Environment.getExternalStorageDirectory(),
                "otr_keystore.ofcaes");
        if (otrKeystoreAES.exists()) {
            //Log.i(TAG, "found " + otrKeystoreAES + "to import");
            doKeyStoreImport = true;
            importOtrKeyStore(otrKeystoreAES, activity);
        }
        else if (intent != null && intent.getData() != null)
        {
            Uri uriData = intent.getData();
            String path = null;

            if(uriData.getScheme() != null && uriData.getScheme().equals("file"))
            {
                path = uriData.toString().replace("file://", "");

                File file = new File(path);

                doKeyStoreImport = true;

                importOtrKeyStore(file, activity);
            }
        }
        else
        {
            Toast.makeText(activity, R.string.otr_keysync_warning_message, Toast.LENGTH_LONG).show();

        }

        return doKeyStoreImport;
    }


    public static void importOtrKeyStore (final File fileOtrKeyStore, final Activity activity)
    {

        try
        {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

            prefs.edit().putString("keystoreimport", fileOtrKeyStore.getCanonicalPath()).apply();
        }
        catch (IOException ioe)
        {
            Log.e("TAG","problem importing key store",ioe);
            return;
        }

        Dialog.OnClickListener ocl = new Dialog.OnClickListener ()
        {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                //launch QR code intent
             //   new IntentIntegrator(activity).initiateScan();

            }
        };


        new AlertDialog.Builder(activity).setTitle(R.string.confirm)
                  .setMessage(R.string.detected_Otr_keystore_import)
                  .setPositiveButton(R.string.yes, ocl) // default button
                  .setNegativeButton(R.string.no, null).setCancelable(true).show();


    }

    public boolean importOtrKeyStoreWithPassword (String fileOtrKeyStore, String importPassword)
    {

        boolean overWriteExisting = true;
        boolean deleteImportedFile = true;
        try {
            return importKeyStore(fileOtrKeyStore, importPassword, overWriteExisting, deleteImportedFile);
        } catch (IOException e) {
           OtrDebugLogger.log("error importing key store",e);
            return false;
        }

    }

    public static boolean handleKeyScanResult (int requestCode, int resultCode, Intent data, Activity activity)
    {
        /**
        IntentResult scanResult =
                IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if  (scanResult != null)
        {

            String otrKeyPassword = scanResult.getContents();

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

            String otrKeyStorePath = prefs.getString("keystoreimport", null);

            Log.d("OTR","got password: " + otrKeyPassword + " for path: " + otrKeyStorePath);

            if (otrKeyPassword != null && otrKeyStorePath != null)
            {

                otrKeyPassword = otrKeyPassword.replace("\n","").replace("\r", ""); //remove any padding, newlines, etc

                try
                {
                    File otrKeystoreAES = new File(otrKeyStorePath);
                    if (otrKeystoreAES.exists()) {
                        try {

                            IOtrKeyManager keyMan = ((ImApp)activity.getApplication()).getRemoteImService().getOtrKeyManager();

                            return keyMan.importOtrKeyStoreWithPassword(otrKeystoreAES.getCanonicalPath(), otrKeyPassword);

                        } catch (Exception e) {

                            OtrDebugLogger.log("error getting keyman",e);
                            return false;
                        }
                    }
                }
                catch (Exception e)
                {
                    Toast.makeText(activity, "unable to open keystore for import", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
            else
            {
                Log.d("OTR","no key store path saved");
                return false;
            }

        }
*/
        return false;
    }


}
