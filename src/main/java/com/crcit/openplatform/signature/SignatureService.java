package com.crcit.openplatform.signature;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * HMAC-SHA256 签名工具。
 *
 * <p>待签名串（每行一个字段，使用 {@code \n} 连接）：</p>
 * <pre>
 * METHOD
 * /path
 * 排序后的query
 * timestamp
 * nonce
 * sha256Hex(body)
 * </pre>
 *
 * <p>约定：</p>
 * <ul>
 *   <li>method 统一转大写；</li>
 *   <li>path 不含 context-path；</li>
 *   <li>query 按字符串排序后拼接（当前只做简单排序，未做 URL 解码归一化）；</li>
 *   <li>timestamp 为毫秒时间戳；</li>
 *   <li>签名结果使用 Base64 编码。</li>
 * </ul>
 */
@Service
public class SignatureService {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /** 生成待签名串。客户端必须使用完全相同的规则。 */
    public String canonicalString(String method, String path, String queryString,
                                  String timestamp, String nonce, byte[] body) {
        return String.join("\n",
                method.toUpperCase(),
                path,
                canonicalQuery(queryString),
                timestamp,
                nonce,
                sha256Hex(body));
    }

    /** 计算 HMAC-SHA256 并做 Base64 编码。 */
    public String signHmacSha256(String secret, String canonicalString) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonicalString.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("cannot calculate signature", e);
        }
    }

    /**
     * 校验签名。
     *
     * <p>必须使用 {@link MessageDigest#isEqual} 做常量时间比较，不能直接 {@code equals}，
     * 否则攻击者可以通过响应时间逐字节猜测签名。</p>
     */
    public boolean verifyHmacSha256(String secret, String canonicalString, String providedSignature) {
        String expected = signHmacSha256(secret, canonicalString);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                providedSignature.getBytes(StandardCharsets.UTF_8));
    }

    /** 计算 body 的 SHA-256 小写十六进制字符串。空 body 使用空数组。 */
    public String sha256Hex(byte[] body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(body == null ? new byte[0] : body);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("cannot calculate sha256", e);
        }
    }

    private String canonicalQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        return Arrays.stream(queryString.split("&"))
                .filter(s -> !s.isBlank())
                .sorted()
                .collect(Collectors.joining("&"));
    }
}
