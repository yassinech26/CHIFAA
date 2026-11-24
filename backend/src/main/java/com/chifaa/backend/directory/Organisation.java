package com.chifaa.backend.directory;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "organisations")
public class Organisation {
    @Id @Column(name="org_id") String id;
    @Column(name="org_name") String name;
    String serviceType;
    String governorate;
    String contactPhone;
    String address;
    String cost;
    LocalDate verifiedDate;
    String verificationMethod;
    boolean fictional = true;
    @Version long revision;
    @ElementCollection
    @CollectionTable(name="organisation_languages", joinColumns=@JoinColumn(name="org_id"))
    @Column(name="language")
    Set<String> languages = new LinkedHashSet<>();
}
