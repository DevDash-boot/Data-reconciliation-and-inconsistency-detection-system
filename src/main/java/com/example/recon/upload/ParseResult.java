package com.example.recon.upload;

import java.util.List;

/** CSV 파싱 결과. totalRows 는 빈 줄을 제외한 데이터 행 수. */
public record ParseResult<T>(int totalRows, List<T> valid, List<RowError> errors) {
    public int validRows() {
        return valid.size();
    }

    public int errorRows() {
        return errors.size();
    }
}
