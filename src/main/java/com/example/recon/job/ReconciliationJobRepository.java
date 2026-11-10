package com.example.recon.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReconciliationJobRepository extends JpaRepository<ReconciliationJob, Long> {

    List<ReconciliationJob> findTop50ByOrderByIdDesc();

    /** PENDING / RUNNING / SUCCESS 인 동일 Job (active_key UNIQUE 라서 최대 1건) */
    Optional<ReconciliationJob> findByActiveKey(String activeKey);
}
