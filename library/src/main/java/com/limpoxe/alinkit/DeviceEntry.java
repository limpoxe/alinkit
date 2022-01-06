package com.limpoxe.alinkit;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class DeviceEntry implements Serializable {
    private static final String TAG = "DeviceEntry";

    public static final int DISCONNECTED  = 1;//未连接
    public static final int CONNECTED     = 2;//已连接

    private LinkitInfo linkitInfo;
    private int status = DISCONNECTED;

    public DeviceEntry(LinkitInfo linkitInfo) {
        this.linkitInfo = linkitInfo;
    }

    public int getStatus() {
        return status;
    }

    public LinkitInfo getLinkitInfo() {
        return linkitInfo;
    }

    public void login() {
        if (status == DISCONNECTED) {
            doConnect();
        }
    }

    public void logout() {
        if (status == CONNECTED) {
            doDisconnect();
        }
    }

    private void doConnect() {
        boolean success = false;
        if (LinkitInfo.DEV_TYPE_GW.equals(linkitInfo.deviceType)) {
            success = gwOnline();
        } else {
            success = subOnline();
        }
        LogUtil.log("设备连接" + linkitInfo.deviceName + (success?"成功":"失败"));
        if (success) {
            status = CONNECTED;
            onConnectSuccess();
        } else {
            status = DISCONNECTED;
        }
    }

    private void onConnectSuccess() {
        if (LinkitInfo.DEV_TYPE_GW.equals(linkitInfo.deviceType)) {
            GateWay.timestamp();
            List<DeviceEntry> list = DeviceManager.getInstance().getSubDev();
            if (list != null && list.size() > 0) {
                for(DeviceEntry entry : list) {
                    LogUtil.log("有子设备，尝试登录设备：" + entry.getLinkitInfo().deviceName);
                    entry.login();
                }
            } else {
                LogUtil.log("未发现子设备");
            }
        }
    }

    private void doDisconnect() {
        if (LinkitInfo.DEV_TYPE_GW.equals(linkitInfo.deviceType)) {
            gwOffline();
        } else {
            subOffline();
        }
        status = DISCONNECTED;
    }

    private boolean gwOnline() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final Boolean[] result = {null};
        GateWay.connect(DeviceManager.getInstance().getContext(),
            linkitInfo.productType,
            linkitInfo.productKey,
            linkitInfo.productSecret,
            linkitInfo.deviceName,
            linkitInfo.deviceSecret,
            new GateWay.OnActionSubDeviceListener() {
                @Override
                public void onSuccess() {
                    result[0] = true;
                    countDownLatch.countDown();
                }
                @Override
                public void onFailed(String code, String msg) {
                    result[0] = false;
                    countDownLatch.countDown();
                }
            });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result[0];
    }

    private boolean subOnline() {
        //登陆子设备的前提是网关已上线
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final Boolean[] result = {null};
        GateWay.subDeviceOnline(
            linkitInfo.deviceName,
            linkitInfo.deviceSecret,
            new GateWay.OnActionSubDeviceListener() {
                @Override
                public void onSuccess() {
                    result[0] = true;
                    countDownLatch.countDown();
                }
                @Override
                public void onFailed(String code, String msg) {
                    result[0] = false;
                    countDownLatch.countDown();
                }
            });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return result[0];
    }

    private void gwOffline() {
        List<DeviceEntry> list = DeviceManager.getInstance().getSubDev();
        if (list != null && list.size() > 0) {
            for(DeviceEntry entry : list) {
                entry.logout();
            }
        }
        LogUtil.log("断开网关设备");
        GateWay.disconnect();
    }

    private void subOffline() {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final Boolean[] result = {null};
        GateWay.subDeviceOffline(
            linkitInfo.deviceName,
            linkitInfo.deviceSecret,
            new GateWay.OnActionSubDeviceListener() {
                @Override
                public void onSuccess() {
                    result[0] = true;
                    countDownLatch.countDown();
                }
                @Override
                public void onFailed(String code, String msg) {
                    result[0] = false;
                    countDownLatch.countDown();
                }
            });
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void pingCloud() {
        if (LinkitInfo.DEV_TYPE_GW.equals(linkitInfo.deviceType)) {
            GateWay.ping(linkitInfo.deviceName, new GateWay.OnActionListener() {
                @Override
                public void onSuccess() {
                    LogUtil.log("ping设备成功：" + linkitInfo.deviceName);
                }
                @Override
                public void onFailed(String code, String msg) {
                    LogUtil.log("ping设备失败：" + linkitInfo.deviceName);
                }
            });
        }
    }

}
