package com.example.recon.mismatch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MismatchRepository extends JpaRepository<Mismatch, Long> {

    List<Mismatch> findByJobId(Long jobId);

    long countByJobIdAndType(Long jobId, MismatchType type);
}
