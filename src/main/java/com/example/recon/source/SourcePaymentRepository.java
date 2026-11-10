package com.example.recon.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourcePaymentRepository extends JpaRepository<SourcePayment, Long> {

    List<SourcePayment> findByBatchId(Long batchId);

    long countByBatchId(Long batchId);
}
