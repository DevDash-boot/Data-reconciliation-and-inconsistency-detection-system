package com.example.recon.job;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class ReconciliationJobController {

    private final ReconciliationJobService jobService;

    /**
     * 새로 실행하면 201, 이미 있는 결과를 재사용하면 200 (응답의 reused=true).
     * curl -X POST localhost:8080/api/jobs -H 'Content-Type: application/json'
     *      -d '{"paymentBatchId":1,"orderBatchId":2,"strategy":"EXACT","force":false}'
     */
    @PostMapping
    public ResponseEntity<JobResponse> run(@RequestBody JobRequest request) {
        JobResponse response = jobService.run(request);
        return ResponseEntity.status(response.reused() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable Long id) {
        return jobService.get(id);
    }

    @GetMapping
    public List<JobResponse> list() {
        return jobService.list();
    }
}
