package com.chifaa.backend.directory;

import java.time.LocalDate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OrganisationRepository extends JpaRepository<Organisation,String> {
    @Query("""
        select o from Organisation o where
          lower(o.name) like :pattern escape '!'
          and (:governorate = '' or o.governorate = :governorate)
          and (:category = '' or o.serviceType = :category)
          and (:stale = false or o.verifiedDate is null or o.verifiedDate < :cutoff)
        """)
    Page<Organisation> search(@Param("pattern") String pattern, @Param("governorate") String governorate,
        @Param("category") String category, @Param("stale") boolean stale, @Param("cutoff") LocalDate cutoff, Pageable pageable);

    @Query(value="select nextval('organisation_number')", nativeQuery=true)
    long nextNumber();

    @Query("select count(o) from Organisation o where o.verifiedDate is null or o.verifiedDate < :cutoff")
    long countDue(@Param("cutoff") LocalDate cutoff);
}
