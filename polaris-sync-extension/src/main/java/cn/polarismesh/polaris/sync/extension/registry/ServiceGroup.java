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

package cn.polarismesh.polaris.sync.extension.registry;

import java.util.Objects;

public class ServiceGroup {

    private final Service service;
    private final String groupName;

    public ServiceGroup(Service service, String groupName) {
        this.service = service;
        this.groupName = groupName;
    }

    public Service getService() {
        return service;
    }

    public String getGroupName() {
        return groupName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceGroup)) {
            return false;
        }
        ServiceGroup that = (ServiceGroup) o;
        return Objects.equals(service, that.service) &&
                Objects.equals(groupName, that.groupName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(service, groupName);
    }

    @Override
    public String toString() {
        return "ServiceGroup{" +
                "service=" + service +
                ", groupName='" + groupName + '\'' +
                '}';
    }
}
