package com.civileng.marketplace.procurement.model;

/** A catalogue is ACTIVE when saved; a contract is PROPOSED until the buyer accepts or declines it. */
public enum PriceListStatus {
    ACTIVE, PROPOSED, DECLINED, TERMINATED
}
