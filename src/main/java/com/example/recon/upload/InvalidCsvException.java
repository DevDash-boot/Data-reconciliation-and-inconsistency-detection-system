package com.example.recon.upload;

/** 파일 전체를 처리할 수 없는 오류 (빈 파일, 헤더 불일치 등). */
public class InvalidCsvException extends RuntimeException {
    public InvalidCsvException(String message) {
        super(message);
    }
}
