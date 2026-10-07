package com.minicalendly.repository;

import com.minicalendly.domain.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    boolean existsBySlotId(Long slotId);

    /** A meeting organised on the given calendar. */
    @Query("""
            select m from Meeting m join fetch m.slot s join fetch s.calendar c
            where m.id = :id and c.id = :calendarId""")
    Optional<Meeting> findOrganisedBy(@Param("id") Long id, @Param("calendarId") Long calendarId);

    /** A meeting the user either organises or participates in. */
    @Query("""
            select m from Meeting m join fetch m.slot s join fetch s.calendar c
            where m.id = :id
              and (c.id = :calendarId
                   or m.id in (select m2.id from Meeting m2 join m2.participants p where p.userId = :userId))""")
    Optional<Meeting> findVisibleTo(@Param("id") Long id,
                                    @Param("calendarId") Long calendarId,
                                    @Param("userId") Long userId);

    @Query(value = """
            select m from Meeting m join fetch m.slot s join fetch s.calendar c
            where s.startTime < :to and s.endTime > :from
              and (c.id = :calendarId
                   or m.id in (select m2.id from Meeting m2 join m2.participants p where p.userId = :userId))
            order by s.startTime""",
            countQuery = """
            select count(m) from Meeting m join m.slot s
            where s.startTime < :to and s.endTime > :from
              and (s.calendar.id = :calendarId
                   or m.id in (select m2.id from Meeting m2 join m2.participants p where p.userId = :userId))""")
    Page<Meeting> findVisibleTo(@Param("calendarId") Long calendarId,
                                @Param("userId") Long userId,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                Pageable pageable);
}
