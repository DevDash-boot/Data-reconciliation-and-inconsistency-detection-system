package com.example.recon.mismatch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MismatchStatusHistoryRepository extends JpaRepository<MismatchStatusHistory, Long> {

    List<MismatchStatusHistory> findByMismatchIdOrderByIdAsc(Long mismatchId);
}
