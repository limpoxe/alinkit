package com.limpoxe.alinkit;

import java.io.Serializable;

public class LinkitInfo implements Serializable {
    public static final String NODE_TYPE_GW = "GATEWAY";
    public static final String NODE_TYPE_SUB = "SUB";

    public String productKey;//阿里云的产品key
    public String deviceName;
    public String deviceSecret;
    public String nodeType;//当前设备的节点类型，是网关还是子设备 DEV_TYPE_GW，DEV_TYPE_SUB

    public LinkitInfo(String productKey, String deviceName, String deviceSecret, String nodeType) {
        this.productKey = productKey;
        this.deviceName = deviceName;
        this.deviceSecret = deviceSecret;
        this.nodeType = nodeType;
    }

    @Override
    public String toString() {
        return "LinkitInfo{" +
            "productKey='" + productKey + '\'' +
            ", nodeType='" + nodeType + '\'' +
            ", deviceName='" + deviceName + '\'' +
            ", deviceSecret='" + deviceSecret + '\'' +
            '}';
    }
}
