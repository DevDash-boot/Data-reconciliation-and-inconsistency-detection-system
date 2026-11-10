package com.example.recon.upload;

import com.example.recon.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final UploadBatchRepository batchRepository;

    /** curl -F type=PAYMENT -F file=@payments.csv localhost:8080/api/uploads */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResult> upload(@RequestParam("type") String type,
                                               @RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new BadRequestException("빈 파일입니다");
        }
        UploadResult result = uploadService.upload(parseType(type), file.getOriginalFilename(), file.getInputStream());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public List<UploadBatchResponse> list() {
        return batchRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))
                .stream().map(UploadBatchResponse::from).toList();
    }

    private UploadType parseType(String type) {
        try {
            return UploadType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("type 은 PAYMENT 또는 ORDER 여야 합니다: " + type);
        }
    }
}
