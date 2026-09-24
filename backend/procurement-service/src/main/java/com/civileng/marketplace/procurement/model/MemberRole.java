package com.civileng.marketplace.procurement.model;

/** OWNER manages the organization and its members; OWNER and APPROVER may approve purchase orders above the threshold; everyone may raise and receive. */
public enum MemberRole {
    OWNER, APPROVER, MEMBER
}
