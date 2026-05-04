package self.sai.stock.AlgoTrading.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "settings",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_settings_key_group",
           columnNames = {"`key`", "group_name"}
       ))
public class Setting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Maps to column group_name (avoids the reserved word 'group'). */
    @Column(name = "group_name", length = 255)
    private String groupName;

    /** Maps to column `key` (backtick-quoted so MySQL doesn't treat it as reserved). */
    @Column(name = "`key`", length = 255)
    private String key;

    @Column(name = "value", columnDefinition = "TEXT")
    private String value;

    public Setting() {}

    public Long   getId()                  { return id; }
    public void   setId(Long id)           { this.id = id; }
    public String getGroupName()           { return groupName; }
    public void   setGroupName(String g)   { this.groupName = g; }
    public String getKey()                 { return key; }
    public void   setKey(String k)         { this.key = k; }
    public String getValue()               { return value; }
    public void   setValue(String v)       { this.value = v; }
}
