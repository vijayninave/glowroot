/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.container.javaagent;

import java.util.Map;

import checkers.nullness.quals.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.base.Objects;

import io.informant.container.config.PluginConfig;

import static io.informant.container.common.ObjectMappers.checkRequiredProperty;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@JsonIgnoreProperties("descriptors")
class PluginConfigSection {

    private final Map<String, PluginConfig> configs;

    @JsonCreator
    PluginConfigSection(@JsonProperty("configs") @Nullable Map<String, PluginConfig> configs)
            throws JsonMappingException {
        checkRequiredProperty(configs, "configs");
        this.configs = configs;
    }

    Map<String, PluginConfig> getConfigs() {
        return configs;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("configs", configs)
                .toString();
    }
}