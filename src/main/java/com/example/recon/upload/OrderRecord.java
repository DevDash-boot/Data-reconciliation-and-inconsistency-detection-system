package com.example.recon.upload;

import java.time.LocalDateTime;

public record OrderRecord(String orderNo, long totalAmount, String status,
                          LocalDateTime orderedAt, int rowNo) {
}
