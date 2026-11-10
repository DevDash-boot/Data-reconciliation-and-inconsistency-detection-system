package com.example.recon.matching;

import com.example.recon.mismatch.MismatchType;

import java.util.List;

public record ReconciliationResult(List<MismatchItem> items) {

    public int count(MismatchType type) {
        int n = 0;
        for (MismatchItem it : items) {
            if (it.type() == type) {
                n++;
            }
        }
        return n;
    }
}
