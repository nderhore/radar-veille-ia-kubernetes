package fr.liveveille.radar.api.signal;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SignalRepository extends JpaRepository<SignalEntity, String> {

    List<SignalEntity> findByPublishedAtAfter(Instant start);

    List<SignalEntity> findAllByOrderByPublishedAtDesc(Pageable page);

    @Query("select s.source, count(s) from SignalEntity s group by s.source")
    List<Object[]> countBySource();
}
