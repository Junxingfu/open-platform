package com.crcit.openplatform.enterprise;

import com.crcit.openplatform.auth.AuthMode;

import java.util.Optional;

/**
 * 可写的企业/应用注册表。
 *
 * <p>开放平台门户需要创建应用、轮换密钥、审批后开通产品，这些操作只给门户服务使用；
 * 开放 API 的认证过滤器仍然只依赖只读的 {@link AppRegistry}，避免业务代码误改数据。</p>
 */
public interface MutableAppRegistry extends AppRegistry {

    /** 注册一个新应用；初始不订阅任何产品，审批通过后再调用 {@link #grantProduct}。 */
    AppRegistration registerApp(String appId, String appSecret, String enterpriseId, AuthMode authMode);

    /** 轮换应用密钥，返回更新后的应用。 */
    Optional<AppRegistration> updateSecret(String appId, String newSecret);

    /**
     * 给应用开通某个 API 产品（创建订阅并更新应用的 productCodes）。
     *
     * @return true 表示开通成功或已经开通；false 表示应用/产品不存在或产品已下线
     */
    boolean grantProduct(String appId, String productCode);
}
