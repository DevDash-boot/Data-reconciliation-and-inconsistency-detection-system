package com.example.recon.mismatch;

import com.example.recon.common.PageResult;

public interface MismatchQueryRepository {

    PageResult<Mismatch> search(MismatchSearchCriteria criteria, int page, int size);
}
