package app.harmonis.identity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

@Entity
@Table(name = "hm_users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "job_title")
    private String jobTitle;

    /** Idioma preferido da interface (pt-BR | en | es). */
    @Column(nullable = false)
    private String language = "pt-BR";

    @Column(name = "is_owner", nullable = false)
    private boolean owner = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private String password;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "hm_user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    public User() {}

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Nomes dos papeis do usuario. */
    public Set<String> roleNames() {
        Set<String> names = new TreeSet<>();
        roles.forEach(r -> names.add(r.getName()));
        return names;
    }

    /** Permissoes efetivas: owner recebe tudo via bypass (resolvido na camada de seguranca). */
    public Set<String> permissionNames() {
        Set<String> perms = new TreeSet<>();
        roles.forEach(r -> r.getPermissions().forEach(p -> perms.add(p.getName())));
        return perms;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public boolean isOwner() { return owner; }
    public void setOwner(boolean owner) { this.owner = owner; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Set<Role> getRoles() { return roles; }
    public void setRoles(Set<Role> roles) { this.roles = roles; }
}
