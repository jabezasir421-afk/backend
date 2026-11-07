package com.bluecollar.search.cache;

import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Component("searchCacheKeyGenerator")
public class SearchCacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder sb = new StringBuilder();
        for (Object param : params) {
            if (param instanceof List<?> list) {
                sb.append(list.stream().map(Objects::toString).sorted().toList());
            } else if (param instanceof Pageable p) {
                sb.append("p:").append(p.getPageNumber())
                        .append(",s:").append(p.getPageSize())
                        .append(",sort:").append(p.getSort());
            } else {
                sb.append(Objects.toString(param, "null"));
            }
            sb.append("|");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return "search:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return "search:" + sb.toString();
        }
    }
}
