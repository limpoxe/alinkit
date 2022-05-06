package com.limpoxe.alinkit;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.aliyun.alink.dm.api.DeviceInfo;
import com.aliyun.alink.dm.api.IGateway;
import com.aliyun.alink.dm.api.IoTApiClientConfig;
import com.aliyun.alink.dm.api.SignUtils;
import com.aliyun.alink.dm.model.ResponseModel;
import com.aliyun.alink.linkkit.api.ILinkKitConnectListener;
import com.aliyun.alink.linkkit.api.IoTDMConfig;
import com.aliyun.alink.linkkit.api.IoTH2Config;
import com.aliyun.alink.linkkit.api.IoTMqttClientConfig;
import com.aliyun.alink.linkkit.api.LinkKit;
import com.aliyun.alink.linkkit.api.LinkKitInitParams;
import com.aliyun.alink.linksdk.channel.core.persistent.PersistentNet;
import com.aliyun.alink.linksdk.channel.core.persistent.mqtt.MqttConfigure;
import com.aliyun.alink.linksdk.channel.gateway.api.subdevice.ISubDeviceActionListener;
import com.aliyun.alink.linksdk.channel.gateway.api.subdevice.ISubDeviceChannel;
import com.aliyun.alink.linksdk.channel.gateway.api.subdevice.ISubDeviceConnectListener;
import com.aliyun.alink.linksdk.channel.gateway.api.subdevice.ISubDeviceRemoveListener;
import com.aliyun.alink.linksdk.cmp.api.ConnectSDK;
import com.aliyun.alink.linksdk.cmp.connect.channel.MqttPublishRequest;
import com.aliyun.alink.linksdk.cmp.connect.channel.MqttRrpcRequest;
import com.aliyun.alink.linksdk.cmp.connect.channel.MqttSubscribeRequest;
import com.aliyun.alink.linksdk.cmp.core.base.AMessage;
import com.aliyun.alink.linksdk.cmp.core.base.ARequest;
import com.aliyun.alink.linksdk.cmp.core.base.AResponse;
import com.aliyun.alink.linksdk.cmp.core.base.ConnectState;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectNotifyListener;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectRrpcHandle;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectRrpcListener;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectSendListener;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectSubscribeListener;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectUnscribeListener;
import com.aliyun.alink.linksdk.id2.Id2ItlsSdk;
import com.aliyun.alink.linksdk.tmp.api.DeviceManager;
import com.aliyun.alink.linksdk.tmp.device.payload.ValueWrapper;
import com.aliyun.alink.linksdk.tmp.network.BluetoothStateMgr;
import com.aliyun.alink.linksdk.tools.AError;
import com.aliyun.alink.linksdk.tools.ALog;
import com.aliyun.alink.linksdk.tools.log.TLogHelper;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GateWay {
    private static final Handler sHandler = new Handler(Looper.getMainLooper());
    private static List<DeviceInfo> sSubList = new ArrayList<>();

    private static String gwProductKey;
    private static String gwProductSecret;
    private static String gwDeviceName;
    private static String gwDeviceSecret;

    private static long deltaTime;

    private static ConnectState status = ConnectState.DISCONNECTED;

    public static String getProductKey() {
        return gwProductKey;
    }

    public static String getProductSecret() {
        return gwProductSecret;
    }

    public static String getDeviceName() {
        return gwDeviceName;
    }

    public static long getSystemTime() {
        return System.currentTimeMillis() + deltaTime;
    }

    public static String getDeviceSecret() {
        return gwDeviceSecret;
    }

    private static IConnectNotifyListener sNotifyListener = new IConnectNotifyListener() {
        /**
         * @param connectId 连接类型，这里判断是否长链 connectId == ConnectSDK.getInstance().getPersistentConnectId()
         * @param connectState {@link ConnectState}
         *     CONNECTED, 连接成功
         *     DISCONNECTED, 已断链
         *     CONNECTING, 连接中
         *     CONNECTFAIL; 连接失败
         */
        @Override
        public void onConnectStateChange(String connectId, ConnectState connectState) {
            LogUtil.log("onConnectStateChange " + connectId + " " + connectState.name());
            status = connectState;
            if (connectState == ConnectState.DISCONNECTED || connectState == ConnectState.CONNECTFAIL) {
                com.limpoxe.alinkit.DeviceManager.getInstance().notifyDisconnected();
            }
        }

        /**
         * 同步服务调用时callType="sync", 则云端下发同步服务请求时会触发这个函数
         * 异步服务调用callType="async", 则云端下发异步服务请求时，触发通过setServiceHandler设置的回调
         * onNotify 会触发的前提是 shouldHandle 没有指定不处理这个topic
         * @param connectId 连接类型，这里判断是否长链 connectId == ConnectSDK.getInstance().getPersistentConnectId()
         * @param topic 下行的topic
         * @param aMessage 下行的数据内容
         */
        @Override
        public void onNotify(String connectId, String topic, AMessage aMessage) {
            String data = new String((byte[]) aMessage.data);
            if (ConnectSDK.getInstance().getPersistentConnectId().equals(connectId) && !TextUtils.isEmpty(topic)
                && topic.startsWith("/ext/rrpc/")) {
                LogUtil.log("收到云端自定义RRPC下行消息：" + connectId + " " + topic + " " + data);
                JSONObject jsonData = JSON.parseObject(data);
                String method = jsonData.getString("method");
                if (method != null && method.startsWith("thing.service.")) {
                    String temp = topic.replace("/ext/rrpc/", "");
                    String productKey = temp.substring(0, temp.indexOf("/"));
                    String deviceName = topic.split(productKey+"/")[1].split("/")[0];
                    String serviceName = method.replace("thing.service.", "");
                    ThingModel.ServiceCallback serviceCallback = ThingModel.getServiceCallback(productKey, deviceName, serviceName);
                    if (serviceCallback != null) {
                        JSONObject paramsJson = jsonData.getJSONObject("params");
                        Map<String, Object> params = null;
                        if (paramsJson != null) {
                            params = paramsJson.getInnerMap();
                        }
                        MqttPublishRequest request = new MqttPublishRequest();
                        // 支持 0 和 1， 默认0
                        // request.qos = 0;
                        request.isRPC = false;
                        request.topic = topic;
                        request.msgId = topic.split("/")[3];
                        LogUtil.log("调用自定义服务处理函数: " + deviceName + " " + serviceName);
                        serviceCallback.handleService(productKey, deviceName, serviceName, params, new ThingModel.ServiceResponser(productKey, deviceName, serviceName, request, null));
                    } else {
                        LogUtil.log("service handler not found: " + serviceName);
                    }
                } else {
                    LogUtil.log(method + " not startsWith thing.service.");
                }
            } else if (ConnectSDK.getInstance().getPersistentConnectId().equals(connectId) && !TextUtils.isEmpty(topic) &&
                topic.startsWith("/sys/") && topic.contains("/rrpc/request/")) {
                LogUtil.log("收到云端系统定义RRPC下行消息：" + connectId + " " + topic + " " + data);
                JSONObject jsonData = JSON.parseObject(data);
                String method = jsonData.getString("method");
                if (method != null && method.startsWith("thing.service.")) {
                    String temp = topic.replace("/sys/", "");
                    String productKey = temp.substring(0, temp.indexOf("/"));
                    String deviceName = topic.split(productKey+"/")[1].split("/")[0];
                    String serviceName = method.replace("thing.service.", "");
                    ThingModel.ServiceCallback serviceCallback = ThingModel.getServiceCallback(productKey, deviceName, serviceName);
                    if (serviceCallback != null) {
                        JSONObject paramsJson = jsonData.getJSONObject("params");
                        Map<String, Object> params = null;
                        if (paramsJson != null) {
                            params = paramsJson.getInnerMap();
                        }
                        MqttPublishRequest request = new MqttPublishRequest();
                        // 支持 0 和 1， 默认0
                        // request.qos = 0;
                        request.isRPC = false;
                        request.topic = topic.replace("request", "response");
                        request.msgId = topic.split("/")[6];
                        LogUtil.log("调用自定义服务处理函数: " + deviceName + " " + serviceName);
                        serviceCallback.handleService(productKey, deviceName, serviceName, params, new ThingModel.ServiceResponser(productKey, deviceName, serviceName, request, null));
                    } else {
                        LogUtil.log("service handler not found: " + serviceName);
                    }
                } else {
                    LogUtil.log(method + " not startsWith thing.service.");
                }
            } else if (ConnectSDK.getInstance().getPersistentConnectId().equals(connectId) && !TextUtils.isEmpty(topic) &&
                topic.startsWith("/sys/") && topic.contains("/broadcast/request/")) {
                LogUtil.log("收到云端批量广播下行：" + connectId + " " + topic + " " + data);
                //无需订阅，云端免订阅，默认无需业务进行ack，但是也支持用户云端和设备端约定业务ack
                //topic 格式：/sys/${pk}/${dn}/broadcast/request/+
            } else if (ConnectSDK.getInstance().getPersistentConnectId().equals(connectId) && !TextUtils.isEmpty(topic) &&
                topic.startsWith("/broadcast/")) {
                LogUtil.log("收到云端广播下行：" + connectId + " " + topic + " " + data);
                //topic 需要用户自己订阅才能收到，topic 格式：/broadcast/${pk}/${自定义action}，需要和云端发送topic一致
            } else if (ConnectSDK.getInstance().getPersistentConnectId().equals(connectId) && !TextUtils.isEmpty(topic)) {
                LogUtil.log("收到topic云端下行：" + connectId + " " + topic + " " + data);
                try {
                    String temp = "";
                    if (topic.startsWith("/sys/")) {
                        temp = topic.replace("/sys/", "");
                    } else if (topic.startsWith("/ext/ntp/")) {
                        temp = topic.replace("/ext/ntp/", "");
                    }
                    String productKey = temp.substring(0, temp.indexOf("/"));
                    String[] ss = topic.split(productKey + "/");
                    String deviceName = ss[1].substring(0, ss[1].indexOf("/"));
                    String normalTopic = topic.replace(productKey, "{productKey}").replace(deviceName, "{deviceName}");
                    ThingModel.TopicCallback topicCallback = ThingModel.getTopicCallback(normalTopic);
                    if (topicCallback != null) {
                        topicCallback.handleTopic(deviceName, data);
                    } else {
                        if (!topic.endsWith("/post_reply") && !topic.contains("/thing/service/")) {
                            LogUtil.log("no handler found for  " + topic + " " + data);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * @param connectId 连接类型，这里判断是否长链 connectId == ConnectSDK.getInstance().getPersistentConnectId()
         * @param topic 下行topic
         * @return 是否要处理这个topic，如果为true，则会回调到onNotify；如果为false，onNotify不会回调这个topic相关的数据。建议默认为true。
         */
        @Override
        public boolean shouldHandle(String connectId, String topic) {
            return true;
        }
    };

    private static void setup(String pt, String pk, String ps, String dn, String ds) {
        gwProductKey = pk;
        gwProductSecret = ps;
        gwDeviceName = dn;
        gwDeviceSecret = ds;
    }

    public static void debugOn() {
        //打开sdk日志打印，便于排查问题
        TLogHelper.setToTlogOn(true);
        PersistentNet.getInstance().openLog(true);
        ALog.setLevel(ALog.LEVEL_DEBUG);
    }

    //主设备上线
    public static void connect(Context context, String pt, String pk, String ps, String dn, String ds, OnActionSubDeviceListener listener) {
        if (status == ConnectState.DISCONNECTED) {
            status = ConnectState.CONNECTING;
            setup(pt, pk, ps, dn, ds);
            LogUtil.log("GateWay连接中[" + getProductKey() + "," + getDeviceName() + "]...");
            doConnect(context, listener);
        } else if (status == ConnectState.CONNECTING) {
            if (listener != null) {
                listener.onFailed("CONNECTING", "正在连接中！");
            }
        } else if (status == ConnectState.CONNECTFAIL) {
            if (listener != null) {
                listener.onFailed("CONNECTFAIL", "连接失败");
            }
        } else {
            if (listener != null) {
                listener.onSuccess();
            }
        }
    }

    private static void doConnect(Context context, OnActionSubDeviceListener listener) {
        MqttConfigure.itlsLogLevel = Id2ItlsSdk.DEBUGLEVEL_NODEBUG;
        // 设置心跳时间，默认65秒
        MqttConfigure.setKeepAliveInterval(65);
        // SDK初始化
        // MqttConfigure.mqttUserName = username;
        // MqttConfigure.mqttPassWord = password;
        // MqttConfigure.mqttClientId = clientId;
        // 构造三元组信息对象
        DeviceInfo deviceInfo = newDeviceInfo(getProductKey(), getDeviceName(), getDeviceSecret());
        //  全局默认域名
        IoTApiClientConfig userData = new IoTApiClientConfig();
        // 设备的一些初始化属性，可以根据云端的注册的属性来设置。
        /**
         * 物模型初始化的初始值
         * 如果这里什么属性都不填，物模型就没有当前设备相关属性的初始值。
         * 用户调用物模型上报接口之后，物模型会有相关数据缓存。
         */
        Map<String, ValueWrapper> propertyValues = new HashMap<>();
        final LinkKitInitParams params = new LinkKitInitParams();
        params.deviceInfo = deviceInfo;
        params.propertyValues = propertyValues;
        params.connectConfig = userData;

        /**
         * 如果用户需要设置域名
         */
        IoTH2Config ioTH2Config = new IoTH2Config();
        //设备端sn，这里使用deviceName替代
        ioTH2Config.clientId = gwDeviceName;
        ioTH2Config.endPoint = "https://" + getProductKey() + ioTH2Config.endPoint;// 线上环境
        params.iotH2InitParams = ioTH2Config;
        Id2ItlsSdk.init(context);
        IoTMqttClientConfig clientConfig = new IoTMqttClientConfig(getProductKey(), getDeviceName(),
            getDeviceSecret());
        clientConfig.receiveOfflineMsg = false;//cleanSession=1 不接受离线消息
        params.mqttClientConfig = clientConfig;

        /**
         * 设备是否支持被飞燕平台APP发现
         * 需要确保开发的APP具备发现该类型设备的权限
         */
        IoTDMConfig ioTDMConfig = new IoTDMConfig();
        // 是否启用本地通信功能，默认不开启，
        // 启用之后会初始化本地通信CoAP相关模块，设备将允许被生活物联网平台的应用发现、绑定、控制，依赖enableThingModel开启
        ioTDMConfig.enableLocalCommunication = false;
        // 是否启用物模型功能，如果不开启，本地通信功能也不支持
        // 默认不开启，开启之后init方法会等到物模型初始化（包含请求云端物模型）完成之后才返回onInitDone
        ioTDMConfig.enableThingModel = true;
        // 是否启用网关功能
        // 默认不开启，开启之后，初始化的时候会初始化网关模块，获取云端网关子设备列表
        ioTDMConfig.enableGateway = true;
        // 默认不开启，是否开启日志推送功能
        ioTDMConfig.enableLogPush = false;

        params.ioTDMConfig = ioTDMConfig;

        LinkKit.getInstance().registerOnPushListener(sNotifyListener);

        /**
         * 设备初始化建联
         * onError 初始化建联失败，如果因网络问题导致初始化失败，需要用户重试初始化
         * onInitDone 初始化成功
         */
        LinkKit.getInstance().init(context, params, new ILinkKitConnectListener() {
            @Override
            public void onError(AError aError) {
                status = ConnectState.DISCONNECTED;
                LogUtil.log("GateWay连接失败，code: " + aError.getCode() + " msg: " + aError.getMsg());
                if (listener != null) {
                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onFailed("" + aError.getCode(), "GateWay失败[" + aError.getMsg() + "]");
                        }
                    });
                }
            }

            @Override
            public void onInitDone(Object o) {
                status = ConnectState.CONNECTED;
                LogUtil.log("GateWay连接成功");
                if (listener != null) {
                    sHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onSuccess();
                        }
                    });
                }
                //设置服务处理函数
                ThingModel.setServiceHandler(getProductKey(), getDeviceName());

                ThingModel.addTopicCallback(new TimestampSyncListener());

                //订阅时间戳同步消息
                subscribe(getProductKey(), getDeviceName(), TimestampSyncListener.TOPIC_SUB);

                //更新本地topo列表
                querySubDevices(null);

                try {
                    BluetoothStateMgr.init(context);
                    BluetoothStateMgr.uninit();
                    DeviceManager.getInstance().stopDiscoverDevices();
                } catch (Exception e)  {
                }
            }
        });
    }

    public static void disconnect() {
        status = ConnectState.DISCONNECTED;
        LogUtil.log("gw disconnect");
        LinkKit.getInstance().deinit();
    }

    //查询主-子拓扑结构
    public static void querySubDevices(OnQuerySubDeviceListener listener) {
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("查询topo列表失败");
            listener.onFailed("-1", "gw device not ready");
            return;
        }
        gateway.gatewayGetSubDevices(new IConnectSendListener() {
            @Override
            public void onResponse(ARequest aRequest, AResponse aResponse) {
                ResponseModel<List<DeviceInfo>> response = JSONObject.parseObject(aResponse.data.toString(),
                    new TypeReference<ResponseModel<List<DeviceInfo>>>() {}.getType());
                if ("200".equals(response.code)) {
                    LogUtil.log("查询topo列表成功");
                    sSubList.clear();
                    if (response.data != null) {
                        sSubList.addAll(response.data);
                    }
                    if (listener != null) {
                        listener.onSuccess(response.data);
                    }
                } else {
                    LogUtil.log("查询topo列表失败");
                    if (listener != null) {
                        listener.onFailed(response.code, null);
                    }
                }
            }

            @Override
            public void onFailure(ARequest aRequest, AError aError) {
                LogUtil.log("查询topo列表失败");
                if (listener != null) {
                    listener.onFailed("" + aError.getCode(), aError.getMsg());
                }
            }
        });
    }

    //添加主-子拓扑结构
    public static void addSubDevice(String productKey, String deviceName, String deviceSecret, OnActionSubDeviceListener listener) {
        final DeviceInfo info = newDeviceInfo(productKey, deviceName, deviceSecret);
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("添加子设备topo失败");
            listener.onFailed("-1", "gw device not ready");
            return;
        }
        gateway.gatewayAddSubDevice(info, new ISubDeviceConnectListener() {
            @Override
            public String getSignMethod() {
                return "hmacsha1";
            }
            @Override
            public String getSignValue() {
                Map<String, String> signMap = new HashMap<>();
                signMap.put("productKey", info.productKey);
                signMap.put("deviceName", info.deviceName);
                signMap.put("clientId", getClientId());
                return SignUtils.hmacSign(signMap, info.deviceSecret);
            }
            @Override
            public String getClientId() {
                return info.getDevId();
            }
            @Override
            public Map<String, Object> getSignExtraData() {
                return null;
            }
            @Override
            public void onConnectResult(boolean isSuccess, ISubDeviceChannel iSubDeviceChannel, AError aError) {
                if (isSuccess) {
                    LogUtil.log("添加子设备topo成功");
                    if (listener != null) {
                        listener.onSuccess();
                    }
                    //更新本地topo列表
                    querySubDevices(null);
                } else {
                    LogUtil.log("添加子设备topo失败");
                    if (listener != null) {
                        listener.onFailed("" + aError.getCode(), aError.getMsg());
                    }
                }
            }

            @Override
            public void onDataPush(String s, AMessage message) {
                // 收到子设备下行数据 topic=" + s  + ", data=" + message
                // 如禁用 删除 已经 设置、服务调用等 返回的数据message.data 是 byte[]
                // 格式例 ：{"method":"thing.service.property.set","id":"184220091","params":{"test":2},"version":"1.0.0"} 示例
                String data = new String((byte[]) message.getData());
                LogUtil.log("子设备收到下行消息:" + deviceName + " " + s + " " + data);
            }
        });
    }

    //主设备代理子设备上线
    public static void subDeviceOnline(String productKey, String deviceName, String deviceSecret, OnActionSubDeviceListener listener) {
        boolean isTopoAdded = false;
        if (sSubList != null) {
            for (DeviceInfo deviceInfo : sSubList) {
                if (deviceInfo.deviceName.equals(deviceName)) {
                    isTopoAdded = true;
                    break;
                }
            }
        }
        if (isTopoAdded) {
            LogUtil.log("子设备在topo列表中:" + deviceName);
        }
        final DeviceInfo info = newDeviceInfo(productKey, deviceName, deviceSecret);
        addSubDevice(productKey, deviceName, deviceSecret, new OnActionSubDeviceListener() {
            @Override
            public void onSuccess() {
                IGateway gateway = LinkKit.getInstance().getGateway();
                if (gateway == null) {
                    LogUtil.log("子设备上线失败:" + deviceName);
                    listener.onFailed("-1", "gw device not ready");
                    return;
                }
                gateway.gatewaySubDeviceLogin(info,
                        new ISubDeviceActionListener() {
                            @Override
                            public void onSuccess() {
                                LogUtil.log("子设备上线成功:" + deviceName);
                                if (listener != null) {
                                    listener.onSuccess();
                                }
                                //设置服务处理函数
                                ThingModel.setSubDeviceServiceHandler(productKey, deviceName, deviceSecret);
                            }

                            @Override
                            public void onFailed(AError aError) {
                                LogUtil.log("子设备上线失败:" + deviceName);
                                if (listener != null) {
                                    listener.onFailed("" + aError.getCode(), aError.getMsg());
                                }
                            }
                        });
            }

            @Override
            public void onFailed(String code, String msg) {
                LogUtil.log("子设备上线失败:" + deviceName);
                if (listener != null) {
                    listener.onFailed("" + code, msg);
                }
            }
        });
    }

    //主设备代理子设备下线
    public static void subDeviceOffline(String productKey, String deviceName, String deviceSecret, OnActionSubDeviceListener listener) {
        final DeviceInfo info = newDeviceInfo(productKey, deviceName, deviceSecret);
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("子设备下线失败:" + deviceName);
            listener.onFailed("-1", "gw device not ready");
            return;
        }
        gateway.gatewaySubDeviceLogout(info, new ISubDeviceActionListener() {
            @Override
            public void onSuccess() {
                LogUtil.log("子设备下线成功:" + deviceName);
                if (listener != null) {
                    listener.onSuccess();
                }
            }

            @Override
            public void onFailed(AError aError) {
                LogUtil.log("子设备下线失败:" + deviceName + " " + aError.getCode() + " " + aError.getMsg());
                if (listener != null) {
                    listener.onFailed("" + aError.getCode(), aError.getMsg());
                }
            }
        });
    }

    //删除主-子拓扑结构
    public static void deleteSubDevice(String productKey, String deviceName, String deviceSecret) {
        final DeviceInfo info = newDeviceInfo(productKey, deviceName, deviceSecret);
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("删除拓扑失败:" + deviceName);
            LogUtil.log("gw device not ready");
            return;
        }
        gateway.gatewayDeleteSubDevice(info,
            new ISubDeviceRemoveListener() {
                @Override
                public void onSuceess() {
                    LogUtil.log("删除拓扑成功:" + deviceName);
                }

                @Override
                public void onFailed(AError aError) {
                    LogUtil.log("删除拓扑失败:" + deviceName + " " + aError.getCode() + " " + aError.getMsg());
                }
            });
    }

    // 代理子设备RRPC同步服务订阅，订阅后收到同步服务调研会触发IConnectNotifyListener.onNotify函数
    public static void subDeviceSubscribe(String productKey, String deviceName, String deviceSecret, String topic) {
        final DeviceInfo info = newDeviceInfo(productKey, deviceName, deviceSecret);
        String realTopic = topic.replace("{deviceName}", deviceName).replace("{productKey}", productKey);
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("订阅失败:" + deviceName + " " + realTopic);
            LogUtil.log("gw device not ready");
            return;
        }
        gateway.gatewaySubDeviceSubscribe(realTopic, info,
            new ISubDeviceActionListener() {
                @Override
                public void onSuccess() {
                    LogUtil.log("订阅成功:" + deviceName + " " + realTopic);
                }

                @Override
                public void onFailed(AError aError) {
                    LogUtil.log("订阅失败:" + deviceName + " " + realTopic + " " + aError.getCode() + " " + aError.getMsg());
                }
            });
    }

    // 代理子设备订阅topic
    // 这个应该是指非物模型的topic
    public void subDeviceUnsubscribe(String productKey, String deviceName, String deviceSecret, String topic) {
        final DeviceInfo info = newDeviceInfo(productKey, deviceName, deviceSecret);
        String realTopic = topic.replace("{deviceName}", deviceName).replace("{productKey}", productKey);
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("取消订阅失败:" + deviceName + " " + realTopic);
            LogUtil.log("gw device not ready");
            return;
        }
        gateway.gatewaySubDeviceUnsubscribe(realTopic, info,
            new ISubDeviceActionListener() {
                @Override
                public void onSuccess() {
                    LogUtil.log("取消订阅成功:" + deviceName + " " + realTopic);
                }

                @Override
                public void onFailed(AError aError) {
                    LogUtil.log("取消订阅失败:" + deviceName + " " + realTopic + " " + aError.getCode() + " " + aError.getMsg());
                }
            });
    }

    // 代理子设备发布
    public void subDevicePublish(String productKey, String deviceName, String deviceSecret, String topic, String data) {
        final DeviceInfo info = newDeviceInfo(productKey, deviceName, deviceSecret);
        String realTopic = topic.replace("{deviceName}", deviceName).replace("{productKey}", productKey);
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("发布失败:" + deviceName + " " + realTopic);
            LogUtil.log("gw device not ready");
            return;
        }
        gateway.gatewaySubDevicePublish(realTopic, data, info,
            new ISubDeviceActionListener() {
                @Override
                public void onSuccess() {
                    LogUtil.log("发布成功:" + deviceName + " " + realTopic);
                }

                @Override
                public void onFailed(AError aError) {
                    LogUtil.log("发布失败:" + deviceName + " " + realTopic + " " + aError.getCode() + " " + aError.getMsg());
                }
            });
    }

    // 订阅
    public static void subscribe(String productKey, String deviceName, String topic) {
        String realTopic = topic.replace("{deviceName}", deviceName).replace("{productKey}", productKey);
        MqttSubscribeRequest request = new MqttSubscribeRequest();
        request.topic = realTopic;
        request.isSubscribe = true;
        LinkKit.getInstance().subscribe(request, new IConnectSubscribeListener() {
            @Override
            public void onSuccess() {
                LogUtil.log("订阅成功:" + deviceName + " " + realTopic);
            }

            @Override
            public void onFailure(AError aError) {
                LogUtil.log("订阅失败:" + deviceName + " " + realTopic);
            }
        });
    }

    // 取消订阅
    public void unsubscribe(String productKey, String deviceName, String topic) {
        String realTopic = topic.replace("{deviceName}", deviceName).replace("{productKey}", productKey);
        MqttSubscribeRequest request = new MqttSubscribeRequest();
        request.topic = realTopic;
        request.isSubscribe = true;
        LinkKit.getInstance().unsubscribe(request,
            new IConnectUnscribeListener() {
                @Override
                public void onSuccess() {
                    LogUtil.log("取消订阅成功:" + deviceName + " " + realTopic);
                }

                @Override
                public void onFailure(AError aError) {
                    LogUtil.log("取消订阅失败:" + deviceName + " " + realTopic + " " + aError.getCode() + " " + aError.getMsg());
                }
            });
    }

    // 发布
    public static void publish(String productKey, String deviceName, String topic, String data, OnActionListener listener) {
        String realTopic = topic.replace("{deviceName}", deviceName).replace("{productKey}", productKey);
        MqttPublishRequest request = new MqttPublishRequest();
        request.topic = realTopic;
        request.qos = 0;
        request.payloadObj = data;
        LinkKit.getInstance().publish(request, new IConnectSendListener() {
            @Override
            public void onResponse(ARequest aRequest, AResponse aResponse) {
                LogUtil.log("发布成功:" + deviceName + " " + realTopic);
                if (listener != null) {
                    listener.onSuccess();
                }
            }

            @Override
            public void onFailure(ARequest aRequest, AError aError) {
                LogUtil.log("发布失败:" + deviceName + " " + realTopic);
                if (listener != null) {
                    listener.onFailed("" + aError.getCode(), aError.getMsg());
                }
            }
        });
    }

    //暂时不知道干啥用的，可能是订阅消息成功后，需要通过这个回调来接收消息？
    public static void addPermitJoinSupport() {
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("gw device not ready");
            return;
        }
        gateway.permitJoin(new IConnectRrpcListener() {
            @Override
            public void onSubscribeSuccess(ARequest aRequest) {
                LogUtil.log("permitJoin 订阅成功");
            }

            @Override
            public void onSubscribeFailed(ARequest aRequest, AError aError) {
                LogUtil.log("permitJoin 订阅失败");
            }

            @Override
            public void onReceived(ARequest aRequest, IConnectRrpcHandle iConnectRrpcHandle) {
                String data = null;
                if (aRequest instanceof MqttRrpcRequest){
                    try {
                        data = new String((byte[]) (((MqttRrpcRequest) aRequest).payloadObj), "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                LogUtil.log("permitJoin 接收到下行数据: " + data);

                //回复云端
                MqttPublishRequest rrpcResponse = new MqttPublishRequest();
                if (aRequest instanceof MqttRrpcRequest) {
                    rrpcResponse.topic = ((MqttRrpcRequest) aRequest).topic;
                }
                //id应该是要从上面的data里面取出来，用来标记是对哪条消息的回复
                //todo cailiming 回复的内容样例，此功能目前没有使用到
                rrpcResponse.payloadObj ="{\"id\":\"123\", \"code\":\"200\"" + ",\"data\":{} }";

                LinkKit.getInstance().publish(rrpcResponse, new IConnectSendListener() {
                    @Override
                    public void onResponse(ARequest aRequest, AResponse aResponse) {
                        LogUtil.log("permitJoin 回复下行数据成功");
                    }

                    @Override
                    public void onFailure(ARequest aRequest, AError aError) {
                        LogUtil.log("permitJoin 回复下行数据失败");
                    }
                });
            }

            @Override
            public void onResponseSuccess(ARequest aRequest) {
                LogUtil.log("permitJoin 回复成功");
            }

            @Override
            public void onResponseFailed(ARequest aRequest, AError aError) {
                LogUtil.log("permitJoin 回复失败");
            }
        });
    }

    public static void timestamp() {
        LogUtil.log("同步时间戳");
        String data = "{\"deviceSendTime\":" + System.currentTimeMillis() + "}";
        publish(getProductKey(), getDeviceName(), TimestampSyncListener.TOPIC_PUB,  data, null);
    }

    public static void ping(String productKey, String deviceName, OnActionListener listener) {
        //利用发布请求时间戳主题的消息来测试设备是否在线，发布成功则表示在线
        String data = "{\"deviceSendTime\":" + System.currentTimeMillis() + "}";
        publish(productKey, deviceName, TimestampSyncListener.TOPIC_PUB,  data, listener);
    }

    static DeviceInfo newDeviceInfo(String productKey, String deviceName, String deviceSecret) {
        DeviceInfo info = new DeviceInfo();
        info.productKey = productKey;
        info.productSecret = null;
        info.deviceName = deviceName;
        info.deviceSecret = deviceSecret;
        return info;
    }

    public static interface OnActionSubDeviceListener {
        public void onSuccess();
        public void onFailed(String code, String msg);
    }

    public static interface OnQuerySubDeviceListener {
        public void onSuccess(List<DeviceInfo> subList);
        public void onFailed(String code, String msg);
    }

    public static interface OnActionListener extends OnActionSubDeviceListener {
    }

    public static class TimestampSyncListener implements ThingModel.TopicCallback {
        public static final String TOPIC_SUB = "/ext/ntp/{productKey}/{deviceName}/response";
        public static final String TOPIC_PUB = "/ext/ntp/{productKey}/{deviceName}/request";

        @Override
        public String getTopic() {
            return TOPIC_SUB;
        }

        @Override
        public void handleTopic(String deviceName, String jsonData) {
            JSONObject data = JSONObject.parseObject(jsonData);
            long deviceSendTime = data.getLong("deviceSendTime");
            long serverRecvTime = data.getLong("serverRecvTime");
            long serverSendTime = data.getLong("serverSendTime");
            //粗略计算
            deltaTime = serverSendTime - System.currentTimeMillis();
            LogUtil.log("timestamp sync: serverSendTime=" + serverSendTime + ", deltaTime=" + deltaTime);
        }
    }
}
