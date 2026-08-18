package ph.dragonview.mobile.data.local;

import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class PasswordHasher {
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;

    private PasswordHasher() {}

    static Credentials hash(String password) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return new Credentials(derive(password, salt), encode(salt));
    }

    static boolean verify(String password, String expectedHash, String encodedSalt)
            throws GeneralSecurityException {
        byte[] actual = Base64.decode(derive(
                password,
                Base64.decode(encodedSalt, Base64.NO_WRAP)), Base64.NO_WRAP);
        byte[] expected = Base64.decode(expectedHash, Base64.NO_WRAP);
        if (actual.length != expected.length) return false;
        int difference = 0;
        for (int index = 0; index < actual.length; index++) {
            difference |= actual[index] ^ expected[index];
        }
        return difference == 0;
    }

    private static String derive(String password, byte[] salt)
            throws GeneralSecurityException {
        PBEKeySpec specification = new PBEKeySpec(
                password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        byte[] key = SecretKeyFactory
                .getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(specification)
                .getEncoded();
        specification.clearPassword();
        return encode(key);
    }

    private static String encode(byte[] value) {
        return Base64.encodeToString(value, Base64.NO_WRAP);
    }

    static final class Credentials {
        final String hash;
        final String salt;
        Credentials(String hash, String salt) {
            this.hash = hash;
            this.salt = salt;
        }
    }
}
