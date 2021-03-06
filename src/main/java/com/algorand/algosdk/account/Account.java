package com.algorand.algosdk.account;


import com.algorand.algosdk.auction.Bid;
import com.algorand.algosdk.auction.SignedBid;
import com.algorand.algosdk.crypto.Address;
import com.algorand.algosdk.crypto.Ed25519PublicKey;
import com.algorand.algosdk.crypto.Signature;
import com.algorand.algosdk.mnemonic.Mnemonic;
import com.algorand.algosdk.transaction.SignedTransaction;
import com.algorand.algosdk.transaction.Transaction;
import com.algorand.algosdk.util.CryptoProvider;
import com.algorand.algosdk.util.Digester;
import com.algorand.algosdk.util.Encoder;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Objects;

/**
 * Create and manage secrets, and perform account-based work such as signing transactions.
 */
public class Account {
    private final KeyPair privateKeyPair;
    private final Address address;
    private static final String KEY_ALGO = "Ed25519";
    private static final String SIGN_ALGO = "EdDSA";
    private static final int PK_SIZE = 32;
    private static final int PK_X509_PREFIX_LENGTH = 12; // Ed25519 specific
    private static final int SK_SIZE = 32;
    private static final int SK_SIZE_BITS = SK_SIZE * 8;
    private static final byte[] TX_SIGN_PREFIX = ("TX").getBytes(StandardCharsets.UTF_8);
    private static final byte[] BID_SIGN_PREFIX = ("aB").getBytes(StandardCharsets.UTF_8);

    /**
     * Account creates a new, random account.
     */
    public Account() throws NoSuchAlgorithmException {
        this((SecureRandom)null);
    }

    /**
     * Create a new account from an existing PKCS8 encoded keypair and Algorand address.
     * @param secretKey existing private key. Must be 32 bytes. Corresponds to seed.
     * @param publicKey existing public key. Must be 32 bytes.
     * @throws NoSuchAlgorithmException if cryptographic provider not configured
     */
    public Account(byte[] secretKey, byte[] publicKey) throws NoSuchAlgorithmException {
        Objects.requireNonNull(secretKey, "secret key must not be null");
        Objects.requireNonNull(publicKey, "public key must not be null");
        if (publicKey.length != PK_SIZE || secretKey.length != SK_SIZE) {
            throw new IllegalArgumentException("Input keys are the wrong size");
        }
        PKCS8EncodedKeySpec pkS = new PKCS8EncodedKeySpec(publicKey);
        PKCS8EncodedKeySpec skS = new PKCS8EncodedKeySpec(secretKey);
        // JCA keypair
        try {
            CryptoProvider.setupIfNeeded();
            KeyFactory kf = KeyFactory.getInstance(KEY_ALGO);
            PublicKey pk = kf.generatePublic(pkS);
            PrivateKey sk = kf.generatePrivate(skS);
            this.privateKeyPair = new KeyPair(pk, sk);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("unexpected behavior", e);
        }
        // now, convert public key to an address
        byte[] raw = this.getClearTextPublicKey();
        this.address = new Address(Arrays.copyOf(raw, raw.length));
    }

    public Account(byte[] seed) throws NoSuchAlgorithmException {
        // seed here corresponds to rfc8037 private key, corresponds to seed in go impl
        // BC for instance takes the seed as private key straight up
        this(new FixedSecureRandom(seed));
    }

    public Account(String mnemonic) throws GeneralSecurityException {
        this(Mnemonic.toKey(mnemonic));
    }

    // randomSrc can be null, in which case system default is used
    private Account(SecureRandom randomSrc) throws NoSuchAlgorithmException {
        CryptoProvider.setupIfNeeded();
        KeyPairGenerator gen = KeyPairGenerator.getInstance(KEY_ALGO);
        if (randomSrc != null) {
            gen.initialize(SK_SIZE_BITS, randomSrc);
        }
        this.privateKeyPair = gen.generateKeyPair();
        // now, convert public key to an address
        byte[] raw = this.getClearTextPublicKey();
        this.address = new Address(Arrays.copyOf(raw, raw.length));
    }

    /**
     * Convenience method for getting the underlying public key for raw operations.
     * @return the public key as length 32 byte array.
     */
    public byte[] getClearTextPublicKey() {
        byte[] b = this.privateKeyPair.getPublic().getEncoded(); // X.509 prepended with ASN.1 prefix
        if (b.length != PK_SIZE + PK_X509_PREFIX_LENGTH) {
            throw new RuntimeException("Generated public key and X.509 prefix is the wrong size");
        }
        byte[] raw = new byte[PK_SIZE];
        System.arraycopy(b, PK_X509_PREFIX_LENGTH, raw, 0, PK_SIZE);
        return raw;
    }

    public Ed25519PublicKey getEd25519PublicKey() {
        return new Ed25519PublicKey(this.getClearTextPublicKey());
    }

    /**
     * Convenience method for getting underlying address.
     * @return account address
     */
    public Address getAddress() {
        return this.address;
    }

    /**
     * Converts the 32 byte private key to a 25 word mnemonic, including a checksum.
     * Refer to the mnemonic package for additional documentation.
     * @return string a 25 word mnemonic
     */
    public String toMnemonic() {
        // this is the only place we use a bouncy castle compile-time dependency
        byte[] X509enc = this.privateKeyPair.getPrivate().getEncoded();
        PrivateKeyInfo pkinfo = PrivateKeyInfo.getInstance(X509enc);
        try {
            ASN1Encodable keyOcts = pkinfo.parsePrivateKey();
            byte[] res = ASN1OctetString.getInstance(keyOcts).getOctets();
            return Mnemonic.fromKey(res);
        } catch (IOException e) {
            throw new RuntimeException("unexpected behavior", e);
        }
    }

    /**
     * Sign a transaction with this account
     * @param tx the transaction to sign
     * @return a signed transaction
     * @throws NoSuchAlgorithmException if signing algorithm could not be found
     */
    public SignedTransaction signTransaction(Transaction tx) throws NoSuchAlgorithmException {
        try {
            byte[] encodedTx = Encoder.encodeToMsgPack(tx);
            // prepend hashable prefix
            byte[] prefixEncodedTx = new byte[encodedTx.length + TX_SIGN_PREFIX.length];
            System.arraycopy(TX_SIGN_PREFIX, 0, prefixEncodedTx, 0, TX_SIGN_PREFIX.length);
            System.arraycopy(encodedTx, 0, prefixEncodedTx, TX_SIGN_PREFIX.length, encodedTx.length);
            // sign
            Signature txSig = signBytes(Arrays.copyOf(prefixEncodedTx, prefixEncodedTx.length));
            String txID = Encoder.encodeToBase32StripPad(Digester.digest(prefixEncodedTx));
            return new SignedTransaction(tx, txSig, txID);
        } catch (IOException e) {
            throw new RuntimeException("unexpected behavior", e);
        }
    }

    /**
     * Sign a canonical msg-pack encoded Transaction
     * @param bytes a canonical msg-pack encoded transaction
     * @return a signed transaction
     * @throws NoSuchAlgorithmException if ed25519 not found on this system
     */
    public SignedTransaction signTransactionBytes(byte[] bytes) throws NoSuchAlgorithmException, IOException {
        try {
            Transaction tx = Encoder.decodeFromMsgPack(bytes, Transaction.class);
            return this.signTransaction(tx);
        } catch (IOException e) {
            throw new IOException("could not decode transaction", e);
        }
    }

    /**
     * Sign a transaction with this account, replacing the fee with the given feePerByte.
     * @param tx transaction to sign
     * @param feePerByte fee per byte, often returned as a suggested fee
     * @return a signed transaction
     * @throws NoSuchAlgorithmException crypto provider not found
     */
    public SignedTransaction signTransactionWithFeePerByte(Transaction tx, BigInteger feePerByte) throws NoSuchAlgorithmException {
        Transaction feeTx = transactionWithSuggestedFeePerByte(tx, feePerByte);
        return this.signTransaction(feeTx);
    }

    /**
     * Sign a bid with this account
     * @param bid the bid to sign
     * @return a signed bid
     */
    public SignedBid signBid(Bid bid) throws NoSuchAlgorithmException {
        try {
            byte[] encodedBid = Encoder.encodeToMsgPack(bid);
            // prepend hashable prefix
            byte[] prefixEncodedBid = new byte[encodedBid.length + BID_SIGN_PREFIX.length];
            System.arraycopy(BID_SIGN_PREFIX, 0, prefixEncodedBid, 0, BID_SIGN_PREFIX.length);
            System.arraycopy(encodedBid, 0, prefixEncodedBid, BID_SIGN_PREFIX.length, encodedBid.length);
            // sign
            Signature bidSig = signBytes(prefixEncodedBid);
            return new SignedBid(bid, bidSig);
        } catch (IOException e) {
            throw new RuntimeException("unexpected behavior", e);
        }
    }

    /**
     * Creates a version of the given transaction with fee populated according to suggestedFeePerByte * estimateTxSize.
     * @param copyTx transaction to populate fee field
     * @param suggestedFeePerByte suggestedFee given by network
     * @return transaction with proper fee set
     * @throws NoSuchAlgorithmException could not estimate tx encoded size.
     */
    static public Transaction transactionWithSuggestedFeePerByte(Transaction copyTx, BigInteger suggestedFeePerByte) throws NoSuchAlgorithmException{
        BigInteger newFee = suggestedFeePerByte.multiply(estimatedEncodedSize(copyTx));
        switch (copyTx.type) {
            case Payment:
                return new Transaction(copyTx.sender, newFee, copyTx.firstValid, copyTx.lastValid, copyTx.note, copyTx.genesisID, copyTx.genesisHash,
                        copyTx.amount, copyTx.receiver, copyTx.closeRemainderTo);
            case KeyRegistration:
                return new Transaction(copyTx.sender, newFee, copyTx.firstValid, copyTx.lastValid, copyTx.note, copyTx.genesisID, copyTx.genesisHash,
                        copyTx.votePK, copyTx.selectionPK);
            case Default:
                throw new IllegalArgumentException("tx cannot have no type");
            default:
                throw new RuntimeException("cannot reach");
        }
    }

    /**
     * EstimateEncodedSize returns the estimated encoded size of the transaction including the signature.
     * This function is useful for calculating the fee from suggested fee per byte.
     * @return an estimated byte size for the transaction.
     */
    public static BigInteger estimatedEncodedSize(Transaction tx) throws NoSuchAlgorithmException {
        Account acc = new Account();
        try {
            return BigInteger.valueOf(Encoder.encodeToMsgPack(acc.signTransaction(tx)).length);
        } catch (IOException e) {
            throw new RuntimeException("unexpected behavior", e);
        }
    }

    /**
     * Sign the given bytes, and wrap in Signature.
     * @param bytes the data to sign
     * @return a signature
     */
    public Signature signBytes(byte[] bytes)  throws NoSuchAlgorithmException {
        try {
            CryptoProvider.setupIfNeeded();
            java.security.Signature signer = java.security.Signature.getInstance(SIGN_ALGO);
            signer.initSign(this.privateKeyPair.getPrivate());
            signer.update(bytes);
            byte[] sigRaw = signer.sign();
            Signature sig = new Signature(sigRaw);
            return sig;
        } catch (InvalidKeyException|SignatureException e) {
            throw new RuntimeException("unexpected behavior", e);
        }
    }

    // Return a pre-set seed in response to nextBytes or generateSeed
    private static class FixedSecureRandom extends SecureRandom {
        private final byte[] fixedValue;
        private int index = 0;

        public FixedSecureRandom(byte[] fixedValue) {
            this.fixedValue = Arrays.copyOf(fixedValue, fixedValue.length);
        }

        @Override
        public void nextBytes(byte[] bytes) {
            if (this.index >= this.fixedValue.length) {
                // no more data to copy
                return;
            }
            int len = bytes.length;
            if (len > this.fixedValue.length - this.index) {
                len = this.fixedValue.length - this.index;
            }
            System.arraycopy(this.fixedValue, this.index, bytes, 0, len);
            this.index += bytes.length;
        }

        @Override
        public byte[] generateSeed(int numBytes) {
            byte[] bytes = new byte[numBytes];
            this.nextBytes(bytes);
            return bytes;
        }
    }

}
