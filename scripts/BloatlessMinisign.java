// Bloatless: minisign keys and signatures without the minisign tool, for the module index
// (android/platform/src/main/assets/bundled/). Same file format as minisign 0.12: a public key is
// base64("Ed" | key id | key), a signature is alg "ED" (Ed25519 over BLAKE2b-512 of the file) plus
// the global signature over signature || trusted comment, as Ed25519Minisign verifies.
//
// Secret keys stay on the maintainer's machine in ~/.bloatless/index/<name>.secret (hex key id and
// hex seed, mode 600), never in the repo. Run with the BouncyCastle jar the build already uses:
//
//   java -cp bcprov-jdk18on-1.86.jar scripts/BloatlessMinisign.java keygen root
//   java -cp ... scripts/BloatlessMinisign.java pub root            # prints the public key line
//   java -cp ... scripts/BloatlessMinisign.java sign index file.json "trusted comment"
import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.math.ec.rfc8032.Ed25519;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public class BloatlessMinisign {
    static final Path DIR = Path.of(System.getProperty("user.home"), ".bloatless", "index");
    static final HexFormat HEX = HexFormat.of();

    public static void main(String[] a) throws IOException {
        switch (a.length == 0 ? "" : a[0]) {
            case "keygen" -> keygen(a[1]);
            case "pub" -> System.out.println(pub(a[1]));
            case "id" -> System.out.println(idHex(load(a[1])[0]));
            case "sign" -> sign(a[1], Path.of(a[2]), a[3]);
            default -> { System.err.println("keygen <name> | pub <name> | id <name> | sign <name> <file> <trusted comment>"); System.exit(2); }
        }
    }

    static void keygen(String name) throws IOException {
        Path f = DIR.resolve(name + ".secret");
        if (Files.exists(f)) throw new IOException(f + " exists; refusing to overwrite a key");
        Files.createDirectories(DIR, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        SecureRandom rng = new SecureRandom();
        byte[] id = new byte[8], sk = new byte[32];
        rng.nextBytes(id);
        rng.nextBytes(sk);
        Files.writeString(f, HEX.formatHex(id) + "\n" + HEX.formatHex(sk) + "\n");
        Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------"));
        System.out.println("key " + idHex(id) + " written to " + f);
    }

    static byte[][] load(String name) throws IOException {
        String[] l = Files.readString(DIR.resolve(name + ".secret")).trim().split("\n");
        return new byte[][]{HEX.parseHex(l[0].trim()), HEX.parseHex(l[1].trim())};
    }

    static String idHex(byte[] id) {
        StringBuilder s = new StringBuilder();
        for (int i = id.length - 1; i >= 0; i--) s.append(String.format("%02X", id[i]));
        return s.toString();
    }

    static String pub(String name) throws IOException {
        byte[][] k = load(name);
        byte[] pk = new byte[32];
        Ed25519.generatePublicKey(k[1], 0, pk, 0);
        byte[] out = new byte[42];
        out[0] = 'E'; out[1] = 'd';
        System.arraycopy(k[0], 0, out, 2, 8);
        System.arraycopy(pk, 0, out, 10, 32);
        return Base64.getEncoder().encodeToString(out);
    }

    static void sign(String name, Path file, String trusted) throws IOException {
        if (trusted.contains("\n")) throw new IOException("the trusted comment is one line");
        byte[][] k = load(name);
        byte[] data = Files.readAllBytes(file);
        Blake2bDigest d = new Blake2bDigest(512);
        d.update(data, 0, data.length);
        byte[] hash = new byte[64];
        d.doFinal(hash, 0);
        byte[] sig = new byte[64];
        Ed25519.sign(k[1], 0, hash, 0, hash.length, sig, 0);
        byte[] t = trusted.getBytes(StandardCharsets.UTF_8);
        byte[] m = new byte[64 + t.length];
        System.arraycopy(sig, 0, m, 0, 64);
        System.arraycopy(t, 0, m, 64, t.length);
        byte[] global = new byte[64];
        Ed25519.sign(k[1], 0, m, 0, m.length, global, 0);
        byte[] first = new byte[74];
        first[0] = 'E'; first[1] = 'D';
        System.arraycopy(k[0], 0, first, 2, 8);
        System.arraycopy(sig, 0, first, 10, 64);
        Base64.Encoder b = Base64.getEncoder();
        String out = "untrusted comment: signature from Bloatless key " + idHex(k[0]) + "\n" + b.encodeToString(first) + "\n"
                + "trusted comment: " + trusted + "\n" + b.encodeToString(global) + "\n";
        Files.writeString(Path.of(file + ".minisig"), out);
        System.out.println("signed " + file + " with " + idHex(k[0]));
    }
}
