/*
 * Copyright 2015-2016 the original author or authors.
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
package org.glowroot.agent.central;

import java.io.IOException;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.glowroot.agent.config.ConfigService;
import org.glowroot.common.config.ImmutableAdvancedConfig;
import org.glowroot.common.config.ImmutableGaugeConfig;
import org.glowroot.common.config.ImmutableInstrumentationConfig;
import org.glowroot.common.config.ImmutableMBeanAttribute;
import org.glowroot.common.config.ImmutablePluginConfig;
import org.glowroot.common.config.ImmutableTransactionConfig;
import org.glowroot.common.config.ImmutableUserRecordingConfig;
import org.glowroot.common.config.InstrumentationConfig.CaptureKind;
import org.glowroot.common.config.PropertyValue;
import org.glowroot.common.live.LiveWeavingService;
import org.glowroot.wire.api.model.ConfigOuterClass.Config;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.AdvancedConfig;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.GaugeConfig;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.InstrumentationConfig;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.MBeanAttribute;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.MethodModifier;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.PluginConfig;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.PluginProperty;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.TransactionConfig;
import org.glowroot.wire.api.model.ConfigOuterClass.Config.UserRecordingConfig;

import static com.google.common.base.Preconditions.checkState;

class ConfigUpdateService {

    private final ConfigService configService;
    private final LiveWeavingService liveWeavingService;

    private final Object lock = new Object();

    ConfigUpdateService(ConfigService configService, LiveWeavingService liveWeavingService) {
        this.configService = configService;
        this.liveWeavingService = liveWeavingService;
    }

    void updateConfig(Config config) throws IOException {
        synchronized (lock) {
            if (config.hasTransactionConfig()) {
                updateTransactionConfig(config.getTransactionConfig());
            }
            if (config.hasUserRecordingConfig()) {
                updateUserRecordingConfig(config.getUserRecordingConfig());
            }
            if (config.hasAdvancedConfig()) {
                updateAdvancedConfig(config.getAdvancedConfig());
            }
            for (PluginConfig pluginConfig : config.getPluginConfigList()) {
                updatePluginConfig(pluginConfig);
            }
            if (config.hasGaugeConfigList()) {
                updateGaugeConfigs(config.getGaugeConfigList().getGaugeConfigList());
            }
            if (config.hasInstrumentationConfigList()) {
                updateInstrumentationConfigs(
                        config.getInstrumentationConfigList().getInstrumentationConfigList());
            }
        }
    }

    int reweave() throws Exception {
        return liveWeavingService.reweave("");
    }

    private void updateTransactionConfig(TransactionConfig config)
            throws IOException {
        ImmutableTransactionConfig.Builder builder = ImmutableTransactionConfig.builder()
                .copyFrom(configService.getTransactionConfig());
        if (config.hasProfilingIntervalMillis()) {
            builder.profilingIntervalMillis(config.getProfilingIntervalMillis().getValue());
        }
        if (config.hasSlowThresholdMillis()) {
            builder.slowThresholdMillis(config.getSlowThresholdMillis().getValue());
        }
        if (config.hasCaptureThreadStats()) {
            builder.captureThreadStats(config.getCaptureThreadStats().getValue());
        }
        configService.updateTransactionConfig(builder.build());
    }

    private void updateUserRecordingConfig(UserRecordingConfig config)
            throws IOException {
        ImmutableUserRecordingConfig.Builder builder = ImmutableUserRecordingConfig.builder()
                .copyFrom(configService.getUserRecordingConfig());
        if (config.hasUsers()) {
            builder.users(config.getUsers().getValueList());
        }
        if (config.hasProfilingIntervalMillis()) {
            builder.profilingIntervalMillis(
                    config.getProfilingIntervalMillis().getValue());
        }
        configService.updateUserRecordingConfig(builder.build());
    }

    private void updateAdvancedConfig(AdvancedConfig config) throws IOException {
        ImmutableAdvancedConfig.Builder builder = ImmutableAdvancedConfig.builder()
                .copyFrom(configService.getAdvancedConfig());
        if (config.hasTimerWrapperMethods()) {
            builder.timerWrapperMethods(config.getTimerWrapperMethods().getValue());
        }
        if (config.hasWeavingTimer()) {
            builder.weavingTimer(config.getWeavingTimer().getValue());
        }
        if (config.hasImmediatePartialStoreThresholdSeconds()) {
            builder.immediatePartialStoreThresholdSeconds(
                    config.getImmediatePartialStoreThresholdSeconds().getValue());
        }
        if (config.hasMaxAggregateTransactionsPerTransactionType()) {
            builder.maxAggregateTransactionsPerTransactionType(
                    config.getMaxAggregateTransactionsPerTransactionType().getValue());
        }
        if (config.hasMaxAggregateQueriesPerQueryType()) {
            builder.maxAggregateQueriesPerQueryType(
                    config.getMaxAggregateQueriesPerQueryType().getValue());
        }
        if (config.hasMaxTraceEntriesPerTransaction()) {
            builder.maxTraceEntriesPerTransaction(
                    config.getMaxTraceEntriesPerTransaction().getValue());
        }
        if (config.hasMaxStackTraceSamplesPerTransaction()) {
            builder.maxStackTraceSamplesPerTransaction(
                    config.getMaxStackTraceSamplesPerTransaction().getValue());
        }
        if (config.hasMbeanGaugeNotFoundDelaySeconds()) {
            builder.mbeanGaugeNotFoundDelaySeconds(
                    config.getMbeanGaugeNotFoundDelaySeconds().getValue());
        }
        configService.updateAdvancedConfig(builder.build());
    }

    private void updatePluginConfig(PluginConfig request) throws IOException {
        String pluginId = request.getId();
        org.glowroot.common.config.PluginConfig existing = configService.getPluginConfig(pluginId);
        if (existing == null) {
            // OK for central to send over plugin configs for plugins that are not present locally
            return;
        }
        ImmutablePluginConfig.Builder builder = ImmutablePluginConfig.builder().copyFrom(existing);
        if (request.hasEnabled()) {
            builder.enabled(request.getEnabled().getValue());
        }
        Map<String, PropertyValue> properties = Maps.newHashMap(existing.properties());
        for (PluginProperty prop : request.getPropertyList()) {
            switch (prop.getValCase()) {
                case BVAL:
                    properties.put(prop.getName(), new PropertyValue(prop.getBval()));
                    break;
                case DVAL_NULL:
                    properties.put(prop.getName(), new PropertyValue(null));
                    break;
                case DVAL:
                    properties.put(prop.getName(), new PropertyValue(prop.getDval()));
                    break;
                case SVAL:
                    properties.put(prop.getName(), new PropertyValue(prop.getSval()));
                    break;
                default:
                    throw new IllegalStateException(
                            "Unexpected plugin property type: " + prop.getValCase());
            }
        }
        builder.properties(properties);
        List<org.glowroot.common.config.PluginConfig> configs =
                Lists.newArrayList(configService.getPluginConfigs());
        boolean found = false;
        for (ListIterator<org.glowroot.common.config.PluginConfig> i = configs.listIterator(); i
                .hasNext();) {
            org.glowroot.common.config.PluginConfig loopPluginConfig = i.next();
            if (loopPluginConfig.id().equals(pluginId)) {
                i.set(builder.build());
                found = true;
                break;
            }
        }
        checkState(found, "Plugin config not found: %s", pluginId);
        configService.updatePluginConfigs(configs);
    }

    private void updateGaugeConfigs(List<GaugeConfig> protos) throws IOException {
        List<org.glowroot.common.config.GaugeConfig> gaugeConfigs =
                Lists.newArrayList();
        for (GaugeConfig proto : protos) {
            gaugeConfigs.add(toCommon(proto));
        }
        configService.updateGaugeConfigs(gaugeConfigs);
    }

    private void updateInstrumentationConfigs(List<InstrumentationConfig> protos)
            throws IOException {
        List<org.glowroot.common.config.InstrumentationConfig> instrumentationConfigs =
                Lists.newArrayList();
        for (InstrumentationConfig proto : protos) {
            instrumentationConfigs.add(toCommon(proto));
        }
        configService.updateInstrumentationConfigs(instrumentationConfigs);
    }

    private static org.glowroot.common.config.GaugeConfig toCommon(GaugeConfig proto) {
        ImmutableGaugeConfig.Builder builder = ImmutableGaugeConfig.builder();
        builder.mbeanObjectName(proto.getMbeanObjectName());
        for (MBeanAttribute mbeanAttribute : proto.getMbeanAttributeList()) {
            builder.addMbeanAttributes(
                    ImmutableMBeanAttribute.builder()
                            .name(mbeanAttribute.getName())
                            .counter(mbeanAttribute.getCounter())
                            .build());
        }
        return builder.build();
    }

    private static org.glowroot.common.config.InstrumentationConfig toCommon(
            InstrumentationConfig proto) {
        ImmutableInstrumentationConfig.Builder builder = ImmutableInstrumentationConfig.builder();
        builder.className(proto.getClassName());
        builder.methodName(proto.getMethodName());
        builder.addAllMethodParameterTypes(proto.getMethodParameterTypeList());
        builder.methodReturnType(proto.getMethodReturnType());
        for (MethodModifier methodModifier : proto.getMethodModifierList()) {
            builder.addMethodModifiers(
                    org.glowroot.common.config.InstrumentationConfig.MethodModifier
                            .valueOf(methodModifier.name()));
        }
        builder.captureKind(CaptureKind.valueOf(proto.getCaptureKind().name()));
        builder.transactionType(proto.getTransactionType());
        builder.transactionNameTemplate(proto.getTransactionNameTemplate());
        builder.transactionUserTemplate(proto.getTransactionUserTemplate());
        builder.putAllTransactionAttributeTemplates(proto.getTransactionAttributeTemplates());
        if (proto.hasTransactionSlowThresholdMillis()) {
            builder.transactionSlowThresholdMillis(
                    proto.getTransactionSlowThresholdMillis().getValue());
        }
        if (proto.hasTraceEntryStackThresholdMillis()) {
            builder.traceEntryStackThresholdMillis(
                    proto.getTraceEntryStackThresholdMillis().getValue());
        }
        builder.traceEntryMessageTemplate(proto.getTraceEntryMessageTemplate());
        builder.traceEntryCaptureSelfNested(proto.getTraceEntryCaptureSelfNested());
        builder.timerName(proto.getTimerName());
        builder.enabledProperty(proto.getEnabledProperty());
        builder.traceEntryEnabledProperty(proto.getTraceEntryEnabledProperty());
        return builder.build();
    }
}
