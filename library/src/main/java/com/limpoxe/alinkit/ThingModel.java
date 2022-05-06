package com.limpoxe.alinkit;

import android.util.Pair;

import com.aliyun.alink.dm.api.BaseInfo;
import com.aliyun.alink.dm.api.DeviceInfo;
import com.aliyun.alink.dm.api.IDMCallback;
import com.aliyun.alink.dm.api.IGateway;
import com.aliyun.alink.dm.api.IThing;
import com.aliyun.alink.dm.api.InitResult;
import com.aliyun.alink.linkkit.api.LinkKit;
import com.aliyun.alink.linksdk.cmp.connect.channel.MqttPublishRequest;
import com.aliyun.alink.linksdk.cmp.core.base.ARequest;
import com.aliyun.alink.linksdk.cmp.core.base.AResponse;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectSendListener;
import com.aliyun.alink.linksdk.tmp.api.InputParams;
import com.aliyun.alink.linksdk.tmp.api.OutputParams;
import com.aliyun.alink.linksdk.tmp.device.payload.CommonResponsePayload;
import com.aliyun.alink.linksdk.tmp.device.payload.ValueWrapper;
import com.aliyun.alink.linksdk.tmp.devicemodel.Arg;
import com.aliyun.alink.linksdk.tmp.devicemodel.Event;
import com.aliyun.alink.linksdk.tmp.devicemodel.Property;
import com.aliyun.alink.linksdk.tmp.devicemodel.Service;
import com.aliyun.alink.linksdk.tmp.listener.IDevRawDataListener;
import com.aliyun.alink.linksdk.tmp.listener.IPublishResourceListener;
import com.aliyun.alink.linksdk.tmp.listener.ITResRequestHandler;
import com.aliyun.alink.linksdk.tmp.listener.ITResResponseCallback;
import com.aliyun.alink.linksdk.tmp.utils.ErrorInfo;
import com.aliyun.alink.linksdk.tmp.utils.GsonUtils;
import com.aliyun.alink.linksdk.tmp.utils.TmpConstant;
import com.aliyun.alink.linksdk.tools.AError;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ThingModel {
    private static final HashMap<String, ServiceCallback> sServiceCallback = new HashMap<>();
    private static final HashMap<String, TopicCallback> sTopicCallback = new HashMap<>();

    public static IThing getTarget(String productKey, String deviceName) {
        if (deviceName.equals(GateWay.getDeviceName())) {
            //主设备
            return LinkKit.getInstance().getDeviceThing();
        } else {
            //子设备
            BaseInfo baseInfo = new BaseInfo();
            baseInfo.productKey = productKey;
            baseInfo.deviceName = deviceName;
            IGateway gateway = LinkKit.getInstance().getGateway();
            if (gateway == null) {
                LogUtil.log("gw device not ready");
                return null;
            }
            Pair<IThing, AError> pair = gateway.getSubDeviceThing(baseInfo);
            if (pair.first == null) {
                LogUtil.log("设备状态异常：" + (pair.second==null?"unkown":(pair.second.getCode() + "-" + pair.second.getMsg())));
            }
            return pair.first;
        }
    }

    //响应云端对设备端的服务调用并回复云端
    static void setServiceHandler(String productKey, String deviceName) {
        IThing iThing = getTarget(productKey, deviceName);
        if (iThing != null) {
            List<Service> services = iThing.getServices();
            if (services != null) {
                for(Service service : services) {
                    LogUtil.log("setServiceHandler " + deviceName + " " + service.getIdentifier());
                    iThing.setServiceHandler(service.getIdentifier(), new ITResRequestHandler() {
                        @Override
                        public void onProcess(String identify, Object result, ITResResponseCallback itResResponseCallback) {
                            LogUtil.log("收到异步服务调用: " + deviceName + " " + identify);
                            ServiceCallback serviceCallback = getServiceCallback(productKey, deviceName, identify);
                            if (serviceCallback != null) {
                                Map<String, ValueWrapper> data = (Map<String, ValueWrapper>) ((InputParams) result).getData();
                                HashMap<String, Object> params = new HashMap();
                                if (data != null) {
                                    Iterator<Map.Entry<String, ValueWrapper>> iterator = data.entrySet().iterator();
                                    while (iterator.hasNext()) {
                                        Map.Entry<String, ValueWrapper> entry = iterator.next();
                                        params.put(entry.getKey(), entry.getValue().getValue());
                                    }
                                }
                                LogUtil.log("调用自定义服务处理函数: " + deviceName + " " + identify);
                                serviceCallback.handleService(productKey, deviceName, identify, params, new ServiceResponser(productKey, deviceName, service.getIdentifier(), null, itResResponseCallback));
                            } else {
                                LogUtil.log("未注册此服务的处理函数: " + deviceName + " " + identify);
                            }
                        }

                        @Override
                        public void onSuccess(Object o, OutputParams outputParams) {
                            //此回调意义不明，像是注册服务成功的回调，又像是回复服务成功的回调
                            LogUtil.log("服务成功: " + deviceName + " " + service.getIdentifier());
                        }

                        @Override
                        public void onFail(Object o, ErrorInfo errorInfo) {
                            //此回调意义不明，像是注册服务失败的回调，又像是回复服务失败的回调
                            LogUtil.log("服务失败: " + deviceName + " " + service.getIdentifier());
                        }
                    });
                }
            } else {
                LogUtil.log("物模型未定义任何服务 " + deviceName);
            }
        } else {
            LogUtil.log("device not ready: " + deviceName);
        }
    }

    //响应云端对设备端的服务调用并回复云端
    //子设备在设置服务前必须先进行一次初始化
    static void setSubDeviceServiceHandler(String productKey, String deviceName, String deviceSecret) {
        final DeviceInfo info = GateWay.newDeviceInfo(productKey, deviceName, deviceSecret);
        //这里可以使用这个map给子设备属性设置初始值
        Map<String, ValueWrapper> subDevInitState = new HashMap<>();
        IGateway gateway = LinkKit.getInstance().getGateway();
        if (gateway == null) {
            LogUtil.log("子设备初始化失败:" + deviceName);
            LogUtil.log("gw device not ready");
            return;
        }
        gateway.initSubDeviceThing(null, info, subDevInitState, new IDMCallback<InitResult>() {
            @Override
            public void onSuccess(InitResult initResult) {
                LogUtil.log("子设备初始化完成:" + deviceName);
                //给子设置设置服务调用响应函数
                setServiceHandler(productKey, deviceName);
            }

            @Override
            public void onFailure(AError aError) {
                LogUtil.log("子设备初始化失败:" + deviceName);
            }
        });
    }

    public static void addServiceCallback(String productKey, String deviceName, String servcieName, ServiceCallback serviceCallback) {
        sServiceCallback.put(productKey + "/" + deviceName + "/" + servcieName, serviceCallback);
    }

    public static ServiceCallback getServiceCallback(String productKey, String deviceName, String servcieName) {
        return sServiceCallback.get(productKey + "/" + deviceName + "/" + servcieName);
    }

    public static void addTopicCallback(TopicCallback topicCallback) {
        sTopicCallback.put(topicCallback.getTopic(), topicCallback);
    }

    public static TopicCallback getTopicCallback(String topic) {
        return sTopicCallback.get(topic);
    }

    /**
     * 二进制数据上报，需要云端配置对应的脚本对数据进行解析
     */
    public static void postRawProperties(String productKey, String deviceName, byte[] rawData) {
        IThing iThing = getTarget(productKey, deviceName);
        if (iThing != null) {
            iThing.thingRawPropertiesPost(rawData, new IDevRawDataListener() {
                @Override
                public void onSuccess(Object o, Object o1) {
                    LogUtil.log("上报属性成功");
                }

                @Override
                public void onFail(Object o, ErrorInfo errorInfo) {
                    LogUtil.log("上报属性失败");
                }
            });
        } else {
            LogUtil.log("device not ready: " + deviceName);
        }
    }

    //上报属性
    public static void postProperty(String productKey, String deviceName, String propertyName, Object valueWrapper) {
        Map<String, Object> properties  = new HashMap<>();
        properties.put(propertyName, valueWrapper);
        postProperties(productKey, deviceName, properties);
    }

    //上报属性
    public static void postProperties(String productKey, String deviceName, Map<String, Object> properties) {
        IThing iThing = getTarget(productKey, deviceName);
        if (iThing != null) {
            List<Property> list = ThingModel.getPropertyNames(productKey, deviceName);
            if (list != null) {
                Map<String, ValueWrapper> propMaps = new HashMap<>();
                for (Property prop : list) {
                    Object value = properties.get(prop.getIdentifier());
                    if (value != null) {
                        if (prop.getIdentifier().contains("password")) {
                            LogUtil.log("postProperties [" + prop.getIdentifier() + ", ******]");
                        } else {
                            LogUtil.log("postProperties [" + prop.getIdentifier() + ", " + value + "]");
                        }
                        if (TmpConstant.TYPE_VALUE_INTEGER.equals(prop.getDataType().getType())) {
                            propMaps.put(prop.getIdentifier(), new ValueWrapper.IntValueWrapper((Integer)value));
                        } else if (TmpConstant.TYPE_VALUE_DOUBLE.equals(prop.getDataType().getType())) {
                            propMaps.put(prop.getIdentifier(), new ValueWrapper.DoubleValueWrapper((Double)value));
                        } else if (TmpConstant.TYPE_VALUE_BOOLEAN.equals(prop.getDataType().getType())) {
                            propMaps.put(prop.getIdentifier(), new ValueWrapper.BooleanValueWrapper((Boolean)value?1:0));
                        } else if (TmpConstant.TYPE_VALUE_TEXT.equals(prop.getDataType().getType())) {
                            propMaps.put(prop.getIdentifier(), new ValueWrapper.StringValueWrapper((String)value));
                        }
                    }
                }
                if (propMaps.size() == 0) {
                    LogUtil.log("no properties: " + deviceName);
                } else {
                    iThing.thingPropertyPost(propMaps, new IPublishResourceListener() {
                        @Override
                        public void onSuccess(String s, Object o) {
                            LogUtil.log("上报属性成功");
                        }

                        @Override
                        public void onError(String s, AError aError) {
                            LogUtil.log("上报属性失败");
                        }
                    });
                }
            } else {
                LogUtil.log("no properties: " + deviceName);
            }
        } else {
            LogUtil.log("device not ready: " + deviceName);
        }
    }

    public static void postEvent(String productKey, String deviceName, String eventName, Map<String, Object> eventData) {
        postEvent(productKey, deviceName, eventName, eventData, null);
    }

    //上报事件
    public static void postEvent(String productKey, String deviceName, String eventName, Map<String, Object> eventData, IPublishResourceListener listener) {
        IThing iThing = getTarget(productKey, deviceName);
        if (iThing != null) {
            List<Event> list = ThingModel.getEventNames(productKey, deviceName);
            if (list != null) {
                for(Event event : list) {
                    if (event.getIdentifier().equals(eventName)) {
                        LogUtil.log("postEvent " + deviceName + " " + eventName + " " + LogUtil.safeToString(eventData));
                        List<Arg> output = event.getOutputData();
                        Map<String, ValueWrapper> data = new HashMap<>();
                        for(Arg arg : output) {
                            if (eventData != null && eventData.get(arg.getIdentifier()) != null) {
                                if (TmpConstant.TYPE_VALUE_INTEGER.equals(arg.getDataType().getType())) {
                                    data.put(arg.getIdentifier(), new ValueWrapper.IntValueWrapper((Integer)eventData.get(arg.getIdentifier())));
                                } else if (TmpConstant.TYPE_VALUE_DOUBLE.equals(arg.getDataType().getType())) {
                                    data.put(arg.getIdentifier(), new ValueWrapper.DoubleValueWrapper(Double.valueOf(String.valueOf(eventData.get(arg.getIdentifier())))));
                                } else if (TmpConstant.TYPE_VALUE_BOOLEAN.equals(arg.getDataType().getType())) {
                                    data.put(arg.getIdentifier(), new ValueWrapper.BooleanValueWrapper((Boolean)eventData.get(arg.getIdentifier())?1:0));
                                } else if (TmpConstant.TYPE_VALUE_TEXT.equals(arg.getDataType().getType())) {
                                    data.put(arg.getIdentifier(), new ValueWrapper.StringValueWrapper((String)eventData.get(arg.getIdentifier())));
                                }
                            }
                        }
                        OutputParams params = new OutputParams(data);
                        iThing.thingEventPost(eventName, params, new IPublishResourceListener() {
                            @Override
                            public void onSuccess(String s, Object o) {
                                LogUtil.log("上报事件成功:" + eventName);
                                if (listener != null) {
                                    listener.onSuccess(s, o);
                                }
                            }

                            @Override
                            public void onError(String s, AError aError) {
                                LogUtil.log("上报事件失败:" + eventName);
                                if (listener != null) {
                                    listener.onError(s, aError);
                                }
                            }
                        });
                        return;
                    }
                }
                LogUtil.log("event not exist: " + deviceName + " " + eventName);
                if (listener != null) {
                    listener.onError("EVENT_NOT_REGISTER", null);
                }
            } else {
                LogUtil.log("no events: " + deviceName + " " + eventName);
                if (listener != null) {
                    listener.onError("EVENT_NOT_REGISTER", null);
                }
            }
        } else {
            LogUtil.log("device not ready: " + deviceName + " " + eventName);
            if (listener != null) {
                listener.onError("DEVICE_NOT_READY", null);
            }
        }
    }

    public static List<Property> getPropertyNames(String productKey, String deviceName) {
        IThing iThing = getTarget(productKey, deviceName);
        if (iThing != null) {
            return iThing.getProperties();
        } else {
            LogUtil.log("device not ready: " + deviceName);
        }
        return null;
    }

    public static ValueWrapper getPropertyValue(String productKey, String deviceName, String propertyName) {
        IThing iThing = getTarget(productKey, deviceName);
        if (iThing != null) {
            return iThing.getPropertyValue(propertyName);
        } else {
            LogUtil.log("device not ready: " + deviceName);
        }
        return null;
    }

    public static List<Event> getEventNames(String productKey, String deviceName) {
        IThing iThing = getTarget(productKey, deviceName);
        if (iThing != null) {
            return iThing.getEvents();
        } else {
            LogUtil.log("device not ready: " + deviceName);
        }
        return null;
    }

    public static List<Service> getServiceNames(String productKey, String deviceName) {
        IThing iThing = getTarget(productKey, deviceName);
        if (iThing != null) {
            return iThing.getServices();
        } else {
            LogUtil.log("device not ready: " + deviceName);
        }
        return null;
    }

    public interface ServiceCallback {
        void handleService(String productKey, String deviceName, String serviceName, Map<String, Object> params, ServiceResponser serviceResponser);
    }

    public interface TopicCallback {
        String getTopic();
        void handleTopic(String deviceName, String jsonData);
    }

    public static final class ServiceResponser {
        private String mProductKey;
        private String mDeviceName;
        private String mServiceName;
        private MqttPublishRequest mSyncResponser;
        private ITResResponseCallback mAsyncResponser;

        public ServiceResponser(String productKey, String deviceName, String serviceName, MqttPublishRequest mqttPublishRequest, ITResResponseCallback callback) {
            this.mProductKey = productKey;
            this.mDeviceName = deviceName;
            this.mServiceName = serviceName;
            this.mSyncResponser = mqttPublishRequest;
            this.mAsyncResponser = callback;
        }

        public void send(Map<String, Object> result) {
            LogUtil.log("回复服务执行成功的结果 " + mDeviceName + " " + mServiceName + " " + LogUtil.safeToString(result));
            List<Service> services = getServiceNames(mProductKey, mDeviceName);
            Service targetService = null;
            if (services != null) {
                for(Service s : services) {
                    if (s.getIdentifier().equals(mServiceName)) {
                        targetService = s;
                        break;
                    }
                }
            }
            if (targetService == null) {
                LogUtil.log("异常-服务未找到 " + mDeviceName + " " + mServiceName + " ");
                return;
            }
            Map<String, ValueWrapper> data = new HashMap<>();
            List<Arg> output = targetService.getOutputData();
            for(Arg arg : output) {
                if (result.get(arg.getIdentifier()) != null) {
                    if (TmpConstant.TYPE_VALUE_INTEGER.equals(arg.getDataType().getType())) {
                        data.put(arg.getIdentifier(), new ValueWrapper.IntValueWrapper((Integer)result.get(arg.getIdentifier())));
                    } else if (TmpConstant.TYPE_VALUE_DOUBLE.equals(arg.getDataType().getType())) {
                        data.put(arg.getIdentifier(), new ValueWrapper.DoubleValueWrapper((Double)result.get(arg.getIdentifier())));
                    } else if (TmpConstant.TYPE_VALUE_BOOLEAN.equals(arg.getDataType().getType())) {
                        data.put(arg.getIdentifier(), new ValueWrapper.BooleanValueWrapper((Boolean)result.get(arg.getIdentifier())?1:0));
                    } else if (TmpConstant.TYPE_VALUE_TEXT.equals(arg.getDataType().getType())) {
                        data.put(arg.getIdentifier(), new ValueWrapper.StringValueWrapper((String)result.get(arg.getIdentifier())));
                    }
                }
            }
            if (mAsyncResponser != null) {
                //异步服务回复
                OutputParams outputParams = new OutputParams(data);
                mAsyncResponser.onComplete(mServiceName, null, outputParams);
            } else {
                //同步服务回复
                CommonResponsePayload<Map<String, ValueWrapper>> payload = new CommonResponsePayload();
                payload.setId(mSyncResponser.msgId);
                payload.setCode(200);
                payload.setData(data);
                mSyncResponser.payloadObj = GsonUtils.toJson(payload);
                LogUtil.log("send : " + mSyncResponser.payloadObj);
                LinkKit.getInstance().publish(mSyncResponser, new IConnectSendListener() {
                    @Override
                    public void onResponse(ARequest aRequest, AResponse aResponse) {
                        LogUtil.log("回复服务执行成功的结果--成功 " + mDeviceName + " " + mServiceName + " ");
                    }
                    @Override
                    public void onFailure(ARequest aRequest, AError aError) {
                        LogUtil.log("回复服务执行成功的结果--失败 " + mDeviceName + " " + mServiceName + " ");
                    }
                });
            }
        }
    }
}
