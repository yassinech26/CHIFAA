package tn.chifaa.users;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name="users")
public class Patient {
    @Id public UUID userPid;
    public String ageBand;
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    @Column(length=5) public String governorate;
    public String preferredLanguage;
    @Column(name="age_confirmed_18plus") public boolean ageConfirmed18plus;
    public String accessChannel;
    public LocalDate registrationDate;
    public LocalDate lastActiveDate;
}
