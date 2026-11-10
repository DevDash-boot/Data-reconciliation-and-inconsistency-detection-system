package com.example.recon.upload;

import java.io.Reader;
import java.util.List;

public final class OrderCsvMapper {

    public static final List<String> HEADER = List.of("order_no", "total_amount", "status", "ordered_at");

    private OrderCsvMapper() {
    }

    public static ParseResult<OrderRecord> parse(Reader reader) {
        return CsvTable.parse(reader, HEADER, (rowNo, c) -> new OrderRecord(
                CsvTable.required(c.get(0), "order_no"),
                CsvTable.parseAmount(c.get(1), "total_amount"),
                CsvTable.required(c.get(2), "status"),
                CsvTable.parseDateTime(c.get(3), "ordered_at"),
                rowNo));
    }
}
