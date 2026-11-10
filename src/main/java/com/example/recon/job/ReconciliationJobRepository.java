package com.example.recon.job;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReconciliationJobRepository extends JpaRepository<ReconciliationJob, Long> {

    List<ReconciliationJob> findTop50ByOrderByIdDesc();
}
