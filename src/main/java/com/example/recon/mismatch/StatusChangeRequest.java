package com.example.recon.mismatch;

/** PATCH /api/mismatches/{id}/status  본문: {"status":"RESOLVED","memo":"원인 확인함"} */
public record StatusChangeRequest(String status, String memo) {
}
