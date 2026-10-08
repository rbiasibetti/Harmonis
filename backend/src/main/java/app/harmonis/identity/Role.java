package app.harmonis.identity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "hm_roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private String label;

    @Column(name = "is_system", nullable = false)
    private boolean system = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "hm_role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {}

    public Role(String name, String label, boolean system) {
        this.name = name;
        this.label = label;
        this.system = system;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isSystem() { return system; }
    public void setSystem(boolean system) { this.system = system; }
    public Set<Permission> getPermissions() { return permissions; }
    public void setPermissions(Set<Permission> permissions) { this.permissions = permissions; }
}
