package com.example.recon.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UploadBatchRepository extends JpaRepository<UploadBatch, Long> {

    /** 해당 유형의 가장 최근 업로드 배치 */
    Optional<UploadBatch> findFirstByTypeOrderByIdDesc(UploadType type);
}
