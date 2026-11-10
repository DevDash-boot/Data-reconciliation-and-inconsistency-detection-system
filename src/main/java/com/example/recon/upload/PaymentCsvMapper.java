package com.example.recon.upload;

import java.io.Reader;
import java.util.List;

public final class PaymentCsvMapper {

    public static final List<String> HEADER = List.of("payment_id", "merchant_uid", "amount", "status", "paid_at");

    private PaymentCsvMapper() {
    }

    public static ParseResult<PaymentRecord> parse(Reader reader) {
        return CsvTable.parse(reader, HEADER, (rowNo, c) -> new PaymentRecord(
                CsvTable.required(c.get(0), "payment_id"),
                CsvTable.required(c.get(1), "merchant_uid"),
                CsvTable.parseAmount(c.get(2), "amount"),
                CsvTable.required(c.get(3), "status"),
                CsvTable.parseDateTime(c.get(4), "paid_at"),
                rowNo));
    }
}
