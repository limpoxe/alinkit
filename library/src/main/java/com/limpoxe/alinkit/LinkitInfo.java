package com.limpoxe.alinkit;

import java.io.Serializable;

public class LinkitInfo implements Serializable {
    public static final String DEV_TYPE_GW     = "GATEWAY";
    public static final String DEV_TYPE_SUB    = "SUB";

    public static final String PRODUCT_TYPE_GW     = "GATEWAY_NODE";
    public static final String PRODUCT_TYPE_SUB    = "SUB_NODE";
    public static final String PRODUCT_TYPE_DIRECT = "DIRECT_NODE";

    public String productType;//阿里云物联网产品节点类型 PRODUCT_TYPE_GW PRODUCT_TYPE_SUB PRODUCT_TYPE_DIRECT
    public String productKey;//阿里云物联网产品key
    public String productSecret;//非必须
    public String deviceType;//当前设备的节点类型，是网关还是子设备 DEV_TYPE_GW，DEV_TYPE_SUB
    public String deviceName;
    public String deviceSecret;

    public LinkitInfo(String productKey, String deviceName, String deviceSecret, String deviceType, String productType) {
        this.productKey = productKey;
        this.productSecret = null;
        this.deviceName = deviceName;
        this.deviceSecret = deviceSecret;
        this.deviceType = deviceType;
        this.productType = productType;
    }

    @Override
    public String toString() {
        return "LinkitInfo{" +
            "productType='" + productType + '\'' +
            ", productKey='" + productKey + '\'' +
            ", deviceType='" + deviceType + '\'' +
            ", deviceName='" + deviceName + '\'' +
            ", deviceSecret='" + deviceSecret + '\'' +
            '}';
    }
}
