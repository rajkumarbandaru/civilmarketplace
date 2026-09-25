package com.civileng.marketplace.tenant.domain;

import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * TXT and CNAME lookups, through the JDK's DNS provider — against a chosen server when
 * {@code platform.domains.dns-server} is set (the local ACME test DNS), else the system resolver.
 */
@Component
public class DnsLookup {

    private final String server;

    public DnsLookup(DomainProperties props) {
        this.server = props.dnsServer();
    }

    public List<String> txt(String name) {
        return records(name, "TXT").stream().map(v -> v.replaceAll("^\"|\"$", "")).toList();
    }

    public List<String> cname(String name) {
        return records(name, "CNAME").stream().map(v -> v.endsWith(".") ? v.substring(0, v.length() - 1) : v).toList();
    }

    List<String> records(String name, String type) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("java.naming.provider.url", server == null || server.isBlank() ? "dns:" : "dns://" + server);
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "2");
        List<String> values = new ArrayList<>();
        try {
            InitialDirContext ctx = new InitialDirContext(env);
            try {
                Attributes attrs = ctx.getAttributes(name, new String[]{type});
                Attribute attr = attrs.get(type);
                if (attr != null) {
                    for (int i = 0; i < attr.size(); i++) {
                        values.add(String.valueOf(attr.get(i)).trim());
                    }
                }
            } finally {
                ctx.close();
            }
        } catch (NamingException e) {
            // NXDOMAIN or no such record: nothing published (yet).
        }
        return values;
    }
}
