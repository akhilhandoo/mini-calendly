package com.minicalendly.repository;

import com.minicalendly.domain.SlotStatus;
import com.minicalendly.domain.TimeSlot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    Optional<TimeSlot> findByIdAndCalendarId(Long id, Long calendarId);

    @Query("""
            select new com.minicalendly.repository.SlotWithMeeting(s, m.id)
            from TimeSlot s left join Meeting m on m.slot = s
            where s.id = :id and s.calendar.id = :calendarId""")
    Optional<SlotWithMeeting> findWithMeeting(@Param("id") Long id, @Param("calendarId") Long calendarId);

    /** True if any slot of the calendar intersects [start, end), ignoring {@code excludeId}. */
    @Query("""
            select count(s) > 0 from TimeSlot s
            where s.calendar.id = :calendarId
              and s.startTime < :end and s.endTime > :start
              and s.id <> :excludeId""")
    boolean existsOverlapping(@Param("calendarId") Long calendarId,
                              @Param("start") Instant start,
                              @Param("end") Instant end,
                              @Param("excludeId") Long excludeId);

    @Query(value = """
            select new com.minicalendly.repository.SlotWithMeeting(s, m.id)
            from TimeSlot s left join Meeting m on m.slot = s
            where s.calendar.id = :calendarId
              and s.startTime < :to and s.endTime > :from
              and (:status is null or s.status = :status)
            order by s.startTime""",
            countQuery = """
            select count(s) from TimeSlot s
            where s.calendar.id = :calendarId
              and s.startTime < :to and s.endTime > :from
              and (:status is null or s.status = :status)""")
    Page<SlotWithMeeting> search(@Param("calendarId") Long calendarId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 @Param("status") SlotStatus status,
                                 Pageable pageable);

    @Query("""
            select new com.minicalendly.repository.SlotInterval(s.calendar.id, s.startTime, s.endTime, s.status)
            from TimeSlot s
            where s.calendar.id in :calendarIds
              and s.startTime < :to and s.endTime > :from
            order by s.calendar.id, s.startTime""")
    List<SlotInterval> findIntervals(@Param("calendarIds") Collection<Long> calendarIds,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);
}
