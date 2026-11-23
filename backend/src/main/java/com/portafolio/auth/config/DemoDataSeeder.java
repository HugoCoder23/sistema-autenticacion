package com.portafolio.auth.config;

import com.portafolio.auth.entity.Role;
import com.portafolio.auth.entity.RoleName;
import com.portafolio.auth.entity.User;
import com.portafolio.auth.repository.RoleRepository;
import com.portafolio.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cuentas de demostración FICTICIAS (dominio .demo, no enrutable). Solo se crean si la base está vacía y
 * SEED_DEMO_DATA=true. La contraseña sale de DEMO_PASSWORD; no hay credenciales reales en el repositorio.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public DemoDataSeeder(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled() || users.count() > 0) return;
        Role user = roles.findByName(RoleName.USER).orElseThrow();
        Role admin = roles.findByName(RoleName.ADMIN).orElseThrow();
        String hash = encoder.encode(props.seed().demoPassword());

        create("Admin Demo", "admin@auth.demo", hash, true, user, admin);
        create("Ana García", "ana@auth.demo", hash, true, user);
        create("Luis Pérez", "luis@auth.demo", hash, true, user);
        create("Sofía Romero", "sofia@auth.demo", hash, true, user);
        create("Carla Ruiz", "carla@auth.demo", hash, false, user);
        log.info("Datos demo cargados: 5 cuentas ficticias (@auth.demo)");
    }

    private void create(String name, String email, String hash, boolean enabled, Role... assigned) {
        User u = new User(name, email, hash);
        u.setEnabled(enabled);
        u.getRoles().addAll(List.of(assigned));
        users.save(u);
    }
}
