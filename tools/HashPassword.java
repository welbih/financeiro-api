import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Requer classpath de runtime do projeto. A senha e lida sem eco no terminal. */
public class HashPassword {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) {
            throw new IllegalStateException("Execute em um terminal interativo");
        }
        char[] password = console.readPassword("Senha do administrador (minimo 12 caracteres): ");
        if (password == null) {
            throw new IllegalArgumentException("Senha nao informada");
        }
        try {
            if (password.length < 12) {
                throw new IllegalArgumentException("Use ao menos 12 caracteres");
            }
            System.out.println(new BCryptPasswordEncoder(12).encode(new String(password)));
        }
        finally {
            Arrays.fill(password, '\0');
        }
    }
}
