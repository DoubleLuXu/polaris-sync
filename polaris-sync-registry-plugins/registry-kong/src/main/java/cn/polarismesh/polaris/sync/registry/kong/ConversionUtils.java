/*
 * Tencent is pleased to support the open source community by making Polaris available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package cn.polarismesh.polaris.sync.registry.kong;

import cn.polarismesh.polaris.sync.common.rest.HostAndPort;
import cn.polarismesh.polaris.sync.extension.registry.Service;
import cn.polarismesh.polaris.sync.extension.utils.ResponseUtils;
import cn.polarismesh.polaris.sync.registry.kong.KongRegistryCenter.ServiceGroup;
import cn.polarismesh.polaris.sync.registry.kong.model.ServiceObject;
import cn.polarismesh.polaris.sync.registry.kong.model.ServiceObjectList;
import cn.polarismesh.polaris.sync.registry.kong.model.TargetObject;
import cn.polarismesh.polaris.sync.registry.kong.model.TargetObjectList;
import cn.polarismesh.polaris.sync.registry.kong.model.UpstreamObject;
import cn.polarismesh.polaris.sync.registry.kong.model.UpstreamObjectList;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencent.polaris.client.pb.ServiceProto.Instance;
import com.tencent.polaris.client.pb.ServiceProto.Instance.Builder;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class ConversionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ConversionUtils.class);

    public static ServiceObject serviceToServiceObject(Service service, String currentSource, String sourceType) {
        ServiceObject serviceObject = new ServiceObject();
        serviceObject.setName(getServiceName(service, currentSource));
        serviceObject.setHost(getUpstreamName(service, KongConsts.GROUP_NAME_DEFAULT, currentSource));
        serviceObject.setPort(KongConsts.PORT_DEFAULT);
        serviceObject.setTags(Collections.singletonList(sourceType));
        serviceObject.setProtocol(KongConsts.PROTOCOL_DEFAULT);
        return serviceObject;
    }

    public static Map<Service, ServiceObject> parseServiceObjects(
            ServiceObjectList serviceObjectList, String sourceName) {
        Map<Service, ServiceObject> values = new HashMap<>();
        List<ServiceObject> data = serviceObjectList.getData();
        if (CollectionUtils.isEmpty(data)) {
            return values;
        }
        for (ServiceObject serviceObject : data) {
            Service service = ConversionUtils.parseServiceName(serviceObject.getName(), sourceName);
            if (null == service) {
                continue;
            }
            values.put(service, serviceObject);
        }
        return values;
    }

    public static Map<String, UpstreamObject> parseUpstreamObjects(
            UpstreamObjectList upstreamObjectList, Service service, String sourceName) {
        Map<String, UpstreamObject> values = new HashMap<>();
        List<UpstreamObject> data = upstreamObjectList.getData();
        if (CollectionUtils.isEmpty(data)) {
            return values;
        }
        for (UpstreamObject upstreamObject : data) {
            ServiceGroup upstreamName = ConversionUtils.parseUpstreamName(upstreamObject.getName(), service, sourceName);
            if (null == upstreamName) {
                continue;
            }
            values.put(upstreamName.getGroupName(), upstreamObject);
        }
        return values;
    }

    public static Map<String, TargetObject> parseTargetObjects(TargetObjectList targetObjectList) {
        Map<String, TargetObject> targetObjectMap = new HashMap<>();
        List<TargetObject> data = targetObjectList.getData();
        if (CollectionUtils.isEmpty(data)) {
            return targetObjectMap;
        }
        for (TargetObject targetObject : data) {
            targetObjectMap.put(targetObject.getTarget(), targetObject);
        }
        return targetObjectMap;
    }

    public static Instance parseTargetToInstance(TargetObject target) {
        Builder builder = Instance.newBuilder();
        builder.setId(ResponseUtils.toStringValue(target.getId()));
        builder.setWeight(ResponseUtils.toUInt32Value(target.getWeight()));
        builder.setHealthy(ResponseUtils.toBooleanValue(true));
        builder.setIsolate(ResponseUtils.toBooleanValue(false));
        String targetValue = target.getTarget();
        HostAndPort hostAndPort = HostAndPort.build(targetValue, KongConsts.PORT_DEFAULT);
        builder.setHost(ResponseUtils.toStringValue(hostAndPort.getHost()));
        builder.setPort(ResponseUtils.toUInt32Value(hostAndPort.getPort()));
        return builder.build();
    }

    public static UpstreamObject groupToUpstreamObject(
            String groupName, Service service, String currentSource, String sourceType) {
        UpstreamObject upstreamObject = new UpstreamObject();
        upstreamObject.setName(getUpstreamName(service, groupName, currentSource));
        upstreamObject.setTags(Collections.singletonList(sourceType));
        return upstreamObject;
    }

    public static TargetObject instanceToTargetObject(String address, Instance instance) {
        TargetObject targetObject = new TargetObject();
        targetObject.setTarget(address);
        targetObject.setWeight(instance.getWeight().getValue());
        return targetObject;
    }

    public static Service parseServiceName(String name, String currentSource) {
        String decodeName = urlDecode(name);
        if (!Pattern.matches("^.+\\..+\\..+$", decodeName)) {
            return null;
        }
        String[] values = name.split("\\.");
        String sourceName = values[0];
        if (!currentSource.equals(sourceName)) {
            return null;
        }
        String namespace = values[1];
        String serviceName = values[2];
        return new Service(namespace,serviceName);
    }

    public static ServiceGroup parseUpstreamName(String name, Service currentService, String currentSource) {
        String decodeName = urlDecode(name);
        if (!Pattern.matches("^.+\\..+\\..+\\..+$", decodeName)) {
            return null;
        }
        String[] values = name.split("\\.");
        String sourceName = values[0];
        if (!currentSource.equals(sourceName)) {
            return null;
        }
        String namespace = values[1];
        String serviceName = values[2];
        if (!currentService.getService().equals(serviceName) || !currentService.getNamespace().equals(namespace)) {
            return null;
        }
        String groupName = values[3];
        return new ServiceGroup(new Service(namespace, serviceName), groupName);
    }

    public static String getServiceName(Service service, String sourceName) {
        String name = sourceName + "." + service.getNamespace() + "." + service.getService();
        return urlEncode(name);
    }

    public static String getUpstreamName(Service service, String groupName, String sourceName) {
        String name = sourceName + "." + service.getNamespace() + "." + service.getService() + "." + groupName;
        return urlEncode(name);
    }

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return value;
    }

    public static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return value;
    }

    public static String marshalJsonText(Object value) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            LOG.error("[Core] fail to serialize object {}", value, e);
        }
        return "";
    }

    public static <T> T unmarshalJsonText(String jsonText, Class<T> clazz) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            return objectMapper.readValue(jsonText, clazz);
        } catch (JsonProcessingException e) {
            LOG.error("[Core] fail to parse json {} to clazz {}", jsonText, clazz.getCanonicalName(), e);
        }
        return null;
    }

}
