package com.example.recon.matching;

/**
 * 대조에 쓰는 가벼운 행. 결제(A)와 주문(B)을 같은 모양으로 맞춘다.
 *   A: key = merchant_uid, amount = amount,       status = status
 *   B: key = order_no,     amount = total_amount, status = status
 */
public record Row(String key, long amount, String status) {
}
