package com.example.recon.upload;

import java.time.LocalDateTime;

public record PaymentRecord(String paymentId, String merchantUid, long amount,
                            String status, LocalDateTime paidAt, int rowNo) {
}
