package com.civileng.marketplace.procurement.service;

import com.civileng.marketplace.procurement.model.*;

import java.util.EnumSet;

final class Fixtures {

    private Fixtures() {
    }

    static Organization org(long id, String name, Capability... caps) {
        Organization o = new Organization();
        o.setId(id);
        o.setName(name);
        o.setCapabilities(caps.length == 0 ? EnumSet.noneOf(Capability.class) : EnumSet.of(caps[0], caps));
        o.setCreatedBy(1L);
        return o;
    }

    static OrgMember member(long orgId, long userId, MemberRole role) {
        OrgMember m = new OrgMember();
        m.setId(orgId * 100 + userId);
        m.setOrganizationId(orgId);
        m.setUserId(userId);
        m.setEmail("u" + userId + "@example.com");
        m.setRole(role);
        m.setAddedBy(userId);
        return m;
    }
}
