package self.sai.stock.AlgoTrading.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users",
       uniqueConstraints = @UniqueConstraint(name = "uq_user_username", columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 100)
    private String username;

    /** BCrypt-hashed password — never store plaintext. */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    public User() {}

    public Long   getId()               { return id; }
    public void   setId(Long id)        { this.id = id; }
    public String getUsername()         { return username; }
    public void   setUsername(String u) { this.username = u; }
    public String getPassword()         { return password; }
    public void   setPassword(String p) { this.password = p; }
    public String getStatus()           { return status; }
    public void   setStatus(String s)   { this.status = s; }
}
