package com.example.recon.upload;

/** 오류가 난 행 정보. rowNo 는 파일의 줄 번호(헤더가 1). */
public record RowError(int rowNo, String message) {
}
