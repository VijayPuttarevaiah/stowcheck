package dev.vijay.stowcheck.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidationRunRepository extends JpaRepository<ValidationRun, Long> {

    List<ValidationRun> findTop50ByOrderByReceivedAtDesc();
}
