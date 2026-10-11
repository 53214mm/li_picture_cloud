package com.li.lipicturecloud.application.airuntime;

import com.li.lipicturecloud.domain.airuntime.ModelProvider;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * 快速连接用的供应商目录端口：读取模型列表，并对选中的模型做一次最小对话验证。
 *
 * <p>两项调用都只返回安全错误码，不暴露端点响应正文或凭据。</p>
 */
public interface ModelCatalogClient {

    /** 读取 {@code {endpoint}/models}。目录接口不存在时返回 {@link Catalog#unlisted()}，由调用方改用推荐模型。 */
    Catalog listModels(URI endpoint, String apiKey, ModelProvider provider);

    /** 用所选模型发一次极小的对话请求，成功即证明凭据与模型都能用于对话。 */
    ConnectivityResult probeChat(URI endpoint, String apiKey, ModelProvider provider, String modelCode);

    /**
     * 一次目录读取的结果。
     *
     * @param result      连通性结果；失败时 {@code modelCodes} 为空
     * @param listed      供应商是否提供了可读的模型目录
     * @param modelCodes  目录中的模型编码（已去重、排序）
     */
    record Catalog(ConnectivityResult result, boolean listed, List<String> modelCodes) {

        public Catalog {
            Objects.requireNonNull(result, "result");
            modelCodes = List.copyOf(modelCodes);
        }

        public static Catalog of(List<String> modelCodes) {
            return new Catalog(ConnectivityResult.success(), true, modelCodes);
        }

        public static Catalog unlisted() {
            return new Catalog(ConnectivityResult.success(), false, List.of());
        }

        public static Catalog failed(String safeErrorCode) {
            return new Catalog(ConnectivityResult.failed(safeErrorCode), false, List.of());
        }
    }
}
