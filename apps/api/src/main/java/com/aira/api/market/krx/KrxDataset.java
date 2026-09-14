package com.aira.api.market.krx;

import java.util.List;

public enum KrxDataset {
    STK_BASE("stk_isu_base_info", "sto", "KOSPI", false,
            "ISU_CD ISU_SRT_CD ISU_NM ISU_ABBRV ISU_ENG_NM LIST_DD MKT_TP_NM SECUGRP_NM SECT_TP_NM KIND_STKCERT_TP_NM PARVAL LIST_SHRS"),
    KSQ_BASE("ksq_isu_base_info", "sto", "KOSDAQ", false,
            "ISU_CD ISU_SRT_CD ISU_NM ISU_ABBRV ISU_ENG_NM LIST_DD MKT_TP_NM SECUGRP_NM SECT_TP_NM KIND_STKCERT_TP_NM PARVAL LIST_SHRS"),
    STK_DAILY("stk_bydd_trd", "sto", "KOSPI", true,
            "BAS_DD ISU_CD ISU_NM MKT_NM SECT_TP_NM TDD_CLSPRC CMPPREVDD_PRC FLUC_RT TDD_OPNPRC TDD_HGPRC TDD_LWPRC ACC_TRDVOL ACC_TRDVAL MKTCAP LIST_SHRS"),
    KSQ_DAILY("ksq_bydd_trd", "sto", "KOSDAQ", true,
            "BAS_DD ISU_CD ISU_NM MKT_NM SECT_TP_NM TDD_CLSPRC CMPPREVDD_PRC FLUC_RT TDD_OPNPRC TDD_HGPRC TDD_LWPRC ACC_TRDVOL ACC_TRDVAL MKTCAP LIST_SHRS"),
    KOSPI_INDEX("kospi_dd_trd", "idx", "KOSPI", true,
            "BAS_DD IDX_CLSS IDX_NM CLSPRC_IDX CMPPREVDD_IDX FLUC_RT OPNPRC_IDX HGPRC_IDX LWPRC_IDX ACC_TRDVOL ACC_TRDVAL MKTCAP"),
    KOSDAQ_INDEX("kosdaq_dd_trd", "idx", "KOSDAQ", true,
            "BAS_DD IDX_CLSS IDX_NM CLSPRC_IDX CMPPREVDD_IDX FLUC_RT OPNPRC_IDX HGPRC_IDX LWPRC_IDX ACC_TRDVOL ACC_TRDVAL MKTCAP");

    private final String apiId, group, market;
    private final boolean dated;
    private final List<String> fields;
    KrxDataset(String apiId, String group, String market, boolean dated, String fields) {
        this.apiId = apiId;
        this.group = group;
        this.market = market;
        this.dated = dated;
        this.fields = List.of(fields.split(" "));
    }
    public String apiId() { return apiId; }
    public String market() { return market; }
    public boolean dated() { return dated; }
    public List<String> fields() { return fields; }
    public boolean stockBase() { return this == STK_BASE || this == KSQ_BASE; }
    public boolean stockDaily() { return this == STK_DAILY || this == KSQ_DAILY; }
    public String url(String basDd) {
        return "https://data-dbg.krx.co.kr/svc/apis/" + group + "/" + apiId + "?basDd=" + basDd;
    }
}
