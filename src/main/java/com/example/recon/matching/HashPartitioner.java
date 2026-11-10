package com.example.recon.matching;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 키를 N 개의 파티션(청크)으로 나눈다.
 *
 * 핵심 성질: 같은 키는 A, B 어디에 있든 항상 같은 파티션에 들어간다.
 * 그래서 파티션별로 따로 대조해도 청크 경계에서 누락/중복이 생기지 않는다.
 *
 * MySQL 의 MOD(CRC32(col), N) 과 같은 값을 낸다. (둘 다 CRC-32 의 부호 없는 32비트 값 사용)
 */
public final class HashPartitioner {

    private HashPartitioner() {
    }

    public static int partition(String key, int partitions) {
        if (partitions <= 0) {
            throw new IllegalArgumentException("partitions 는 1 이상이어야 합니다: " + partitions);
        }
        CRC32 crc = new CRC32();
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        return (int) (crc.getValue() % partitions);
    }
}
