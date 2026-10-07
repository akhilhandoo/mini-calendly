package com.minicalendly.repository;

import com.minicalendly.domain.Calendar;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalendarRepository extends JpaRepository<Calendar, Long> {

    @Query("select c from Calendar c join fetch c.owner where c.owner.id = :userId")
    Optional<Calendar> findByOwnerId(@Param("userId") Long userId);

    /**
     * Row-locks the calendar so that all writes to one user's slots and meetings are
     * serialised. Contention is per user only, so this scales with the number of users.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Calendar c where c.owner.id = :userId")
    Optional<Calendar> lockByOwnerId(@Param("userId") Long userId);

    @Query("select c from Calendar c where c.owner.id in :userIds")
    List<Calendar> findByOwnerIds(@Param("userIds") Collection<Long> userIds);
}
