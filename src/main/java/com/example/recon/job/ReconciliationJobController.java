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

    /** curl -X POST localhost:8080/api/jobs -H 'Content-Type: application/json'
     *       -d '{"paymentBatchId":1,"orderBatchId":2,"strategy":"EXACT"}' */
    @PostMapping
    public ResponseEntity<JobResponse> run(@RequestBody JobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.run(request));
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
