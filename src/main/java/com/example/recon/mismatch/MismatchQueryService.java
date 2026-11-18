package com.example.recon.mismatch;

import com.example.recon.common.PageResult;
import com.example.recon.common.exception.BadRequestException;
import com.example.recon.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MismatchQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    private final MismatchRepository mismatchRepository;

    public PageResult<MismatchResponse> search(MismatchSearchCriteria criteria, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page 는 0 이상이어야 합니다");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size 는 1 ~ " + MAX_PAGE_SIZE + " 사이여야 합니다");
        }
        if (criteria.from() != null && criteria.to() != null && criteria.from().isAfter(criteria.to())) {
            throw new BadRequestException("from 은 to 보다 늦을 수 없습니다: " + criteria.from() + " > " + criteria.to());
        }
        return mismatchRepository.search(criteria, page, size).map(MismatchResponse::from);
    }

    public MismatchResponse get(Long id) {
        Mismatch m = mismatchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("불일치를 찾을 수 없습니다: " + id));
        return MismatchResponse.from(m);
    }

    /** 쿼리 파라미터 문자열을 enum 으로 바꾼다. 비어 있으면 null (조건 없음). */
    public static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(type.getSimpleName() + " 값이 올바르지 않습니다: " + value);
        }
    }

    /** 쿼리 파라미터 문자열(yyyy-MM-dd)을 날짜로 바꾼다. 비어 있으면 null (조건 없음). */
    public static LocalDate parseDate(String name, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException(name + " 은 yyyy-MM-dd 형식이어야 합니다: " + value);
        }
    }
}
