package com.example.recon.mismatch;

import java.util.List;

/** PATCH /api/mismatches/status  본문: {"ids":[1,2,3],"status":"IGNORED","memo":"테스트 데이터"} */
public record BulkStatusChangeRequest(List<Long> ids, String status, String memo) {
}
