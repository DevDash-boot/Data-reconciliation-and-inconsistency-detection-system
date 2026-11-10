package com.example.recon.matching;

/** 값이 다른 필드 하나. a, b 는 문자열로 표현한 양쪽 값. */
public record FieldDiff(String field, String a, String b) {
}
