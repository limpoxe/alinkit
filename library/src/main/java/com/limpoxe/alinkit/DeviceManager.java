package com.limpoxe.alinkit;

import android.content.Context;

import com.aliyun.alink.linksdk.cmp.api.ConnectSDK;
import com.aliyun.alink.linksdk.cmp.core.base.ConnectState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class DeviceManager {
    private static final String TAG = "DeviceManager";
    //每隔一段时间检测一次网关连接(利用ntp接口)
    private static final long PING_CLOUD_INTERV = 5 * 60 * 1000;
    private long mLastPingCloudTimeInMs = 0;

    private static DeviceManager sInstance;
    private final Object mLock = new Object();
    private final HashMap<String, DeviceEntry> mDeviceEntries = new HashMap<>();
    private Context mContext;

    private DeviceManager() {
    }

    public static DeviceManager getInstance() {
        if (sInstance == null) {
            synchronized (DeviceManager.class) {
                if (sInstance == null) {
                    sInstance = new DeviceManager();
                }
            }
        }
        return sInstance;
    }

    public void init(Context context) {
        mContext = context.getApplicationContext();
    }

    public Context getContext() {
        return mContext;
    }

    public void addDev(LinkitInfo linkitInfo) {
        synchronized (mLock) {
            if (!mDeviceEntries.containsKey(linkitInfo.deviceName)) {
                LogUtil.log(TAG, "添加设备：" + linkitInfo.deviceName);
                mDeviceEntries.put(linkitInfo.deviceName, new DeviceEntry(linkitInfo));
            }
        }
        beat();
    }

    public void delDev(String deviceName) {
        synchronized (mLock) {
            if (mDeviceEntries.containsKey(deviceName)) {
                DeviceEntry deviceEntry = mDeviceEntries.get(deviceName);
                deviceEntry.logout();
                LogUtil.log(TAG, "删除设备：" + deviceName);
                mDeviceEntries.remove(deviceName);
            }
        }
        beat();
    }

    public DeviceEntry getGwDev() {
        synchronized (mLock) {
            Iterator<Map.Entry<String, DeviceEntry>> itr = mDeviceEntries.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, DeviceEntry> entry = itr.next();
                if (LinkitInfo.NODE_TYPE_GW.equals(entry.getValue().getLinkitInfo().nodeType)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    public List<DeviceEntry> getSubDev() {
        List<DeviceEntry> sub = new ArrayList<>();
        synchronized (mLock) {
            Iterator<Map.Entry<String, DeviceEntry>> itr = mDeviceEntries.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, DeviceEntry> entry = itr.next();
                if (LinkitInfo.NODE_TYPE_SUB.equals(entry.getValue().getLinkitInfo().nodeType)) {
                    sub.add(entry.getValue());
                }
            }
        }
        return sub;
    }

    public DeviceEntry getDev(String deviceName) {
        synchronized (mLock) {
            return mDeviceEntries.get(deviceName);
        }
    }

    public ConnectState getLinkConnectState() {
        return ConnectSDK.getInstance().getConnectState(ConnectSDK.getInstance().getPersistentConnectId());
    }

    public void beat() {
        DeviceEntry deviceEntry = getGwDev();
        if (deviceEntry != null) {
            if (deviceEntry.getStatus() == DeviceEntry.CONNECTED) {
                if (System.currentTimeMillis() - mLastPingCloudTimeInMs > PING_CLOUD_INTERV) {
                    LogUtil.log(TAG, "每隔[ms:" + PING_CLOUD_INTERV +"]ping一次云端(利用NTP接口)");
                    mLastPingCloudTimeInMs = System.currentTimeMillis();
                    deviceEntry.pingCloud();
                }

                List<DeviceEntry> list = getSubDev();
                if (list != null && list.size() > 0) {
                    for(DeviceEntry entry : list) {
                        if (entry.getStatus() == DeviceEntry.CONNECTED) {
                            //entry.pingCloud();
                        } else {
                            LogUtil.log(TAG, "子设备不在线，尝试上线：" + entry.getLinkitInfo().deviceName);
                            entry.login();
                        }
                    }
                }
            } else {
                LogUtil.log(TAG, "网关设备不在线，尝试上线：" + deviceEntry.getLinkitInfo().deviceName);
                deviceEntry.login();
            }
        } else {
            LogUtil.log(TAG, "未发现网关设备");
        }
    }

    public void notifyDisconnected() {
        synchronized (mLock) {
            Iterator<Map.Entry<String, DeviceEntry>> itr = mDeviceEntries.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, DeviceEntry> entry = itr.next();
                LogUtil.log("setStatus DISCONNECTED for " + entry.getValue().getLinkitInfo().deviceName);
                entry.getValue().setStatus(DeviceEntry.DISCONNECTED);
            }
        }
    }
}
