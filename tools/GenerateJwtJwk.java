import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Base64;
import java.util.UUID;

/** Execute com Java 25: java tools/GenerateJwtJwk.java. A saida e uma chave PRIVADA. */
public class GenerateJwtJwk {
    public static void main(String[] args) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(3072);
        var key = (RSAPrivateCrtKey) generator.generateKeyPair().getPrivate();
        System.out.printf(
            "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"%s\","
            + "\"n\":\"%s\",\"e\":\"%s\",\"d\":\"%s\",\"p\":\"%s\",\"q\":\"%s\","
            + "\"dp\":\"%s\",\"dq\":\"%s\",\"qi\":\"%s\"}%n",
            UUID.randomUUID(), encode(key.getModulus()), encode(key.getPublicExponent()),
            encode(key.getPrivateExponent()), encode(key.getPrimeP()), encode(key.getPrimeQ()),
            encode(key.getPrimeExponentP()), encode(key.getPrimeExponentQ()), encode(key.getCrtCoefficient()));
    }

    private static String encode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
