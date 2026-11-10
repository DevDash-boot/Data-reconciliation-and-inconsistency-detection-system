package com.example.recon.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SourceOrderRepository extends JpaRepository<SourceOrder, Long> {

    List<SourceOrder> findByBatchId(Long batchId);

    long countByBatchId(Long batchId);
}
