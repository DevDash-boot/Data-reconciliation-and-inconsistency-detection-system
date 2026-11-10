package com.example.recon.upload;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvMapperTest {

    private static final String PAY_HEADER = "payment_id,merchant_uid,amount,status,paid_at\n";

    @Test
    @DisplayName("정상 결제 CSV 를 레코드로 변환한다")
    void parsesValidPayments() {
        String csv = PAY_HEADER
                + "imp_1,O-1,30000,paid,2026-11-01T10:00:00\n"
                + "imp_2,O-2,8000,cancelled,2026-11-01 11:30:00\n";

        ParseResult<PaymentRecord> r = PaymentCsvMapper.parse(new StringReader(csv));

        assertThat(r.totalRows()).isEqualTo(2);
        assertThat(r.validRows()).isEqualTo(2);
        assertThat(r.errorRows()).isZero();
        assertThat(r.valid().get(0).merchantUid()).isEqualTo("O-1");
        assertThat(r.valid().get(1).rowNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("잘못된 행은 건너뛰고 오류로 모은다 (전체가 죽지 않는다)")
    void collectsRowErrors() {
        String csv = PAY_HEADER
                + "imp_1,O-1,30000,paid,2026-11-01T10:00:00\n"
                + "imp_2,O-2,abc,paid,2026-11-01T10:00:00\n"          // 금액 형식 오류
                + "imp_3,,100,paid,2026-11-01T10:00:00\n"              // 키 없음
                + "imp_4,O-4,100,paid,not-a-date\n"                    // 일시 오류
                + "imp_5,O-5,100,paid\n";                              // 컬럼 부족

        ParseResult<PaymentRecord> r = PaymentCsvMapper.parse(new StringReader(csv));

        assertThat(r.totalRows()).isEqualTo(5);
        assertThat(r.validRows()).isEqualTo(1);
        assertThat(r.errorRows()).isEqualTo(4);
        assertThat(r.errors().get(0).rowNo()).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 줄은 건수에 포함하지 않는다")
    void skipsBlankLines() {
        String csv = PAY_HEADER + "\nimp_1,O-1,100,paid,2026-11-01T10:00:00\n\n";

        assertThat(PaymentCsvMapper.parse(new StringReader(csv)).totalRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTF-8 BOM 이 있어도 헤더를 인식한다")
    void handlesBom() {
        String csv = "\uFEFF" + PAY_HEADER + "imp_1,O-1,100,paid,2026-11-01T10:00:00\n";

        assertThat(PaymentCsvMapper.parse(new StringReader(csv)).validRows()).isEqualTo(1);
    }

    @Test
    @DisplayName("헤더가 다르면 파일 전체를 거부한다")
    void rejectsWrongHeader() {
        assertThatThrownBy(() -> PaymentCsvMapper.parse(new StringReader("a,b,c\n1,2,3\n")))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    @DisplayName("빈 파일은 거부한다")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> OrderCsvMapper.parse(new StringReader("")))
                .isInstanceOf(InvalidCsvException.class);
    }

    @Test
    @DisplayName("주문 CSV 를 레코드로 변환한다")
    void parsesOrders() {
        String csv = "order_no,total_amount,status,ordered_at\nO-1,30000,PAID,2026-11-01T10:00:00\n";

        ParseResult<OrderRecord> r = OrderCsvMapper.parse(new StringReader(csv));

        assertThat(r.valid().get(0).totalAmount()).isEqualTo(30000L);
        assertThat(r.valid().get(0).status()).isEqualTo("PAID");
    }
}
