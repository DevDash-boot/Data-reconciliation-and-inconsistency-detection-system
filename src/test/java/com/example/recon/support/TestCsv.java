package com.example.recon.support;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 테스트용 CSV 문자열/스트림 만들기 */
public final class TestCsv {

    private TestCsv() {
    }

    public static String payments(String... rows) {
        return "payment_id,merchant_uid,amount,status,paid_at\n" + String.join("\n", rows) + "\n";
    }

    public static String orders(String... rows) {
        return "order_no,total_amount,status,ordered_at\n" + String.join("\n", rows) + "\n";
    }

    public static InputStream stream(String csv) {
        return new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));
    }
}
