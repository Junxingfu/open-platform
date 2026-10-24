package com.crcit.openplatform.enterprise;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 企业/应用/API 产品的数据访问接口。
 *
 * <p>当前实现是 {@link InMemoryAppRegistry}。后续接数据库时新增一个 MyBatis/JPA 实现即可，
 * 认证、授权代码不需要改动。</p>
 */
public interface AppRegistry {

    Optional<AppRegistration> findApp(String appId);

    Optional<Enterprise> findEnterprise(String enterpriseId);

    Optional<ApiProduct> findProduct(String productCode);

    Optional<ApiDefinition> findApi(String method, String path);

    /** 企业 + 应用 + 产品 维度的订阅是否仍在有效期内。 */
    boolean hasActiveSubscription(String enterpriseId, String appId, String productCode);

    /** 应用通过已订阅产品获得的全部权限点。 */
    Set<String> scopesForApp(String appId);

    /** 全部 API 产品，供门户展示和选择接入。 */
    List<ApiProduct> listProducts();

    /** 全部 API 定义，供门户目录展示。 */
    List<ApiDefinition> listApis();
}
