/*
 * Copyright 2013-2016 the original author or authors.
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
package org.glowroot.agent.impl;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import org.glowroot.agent.model.CommonTimerImpl;
import org.glowroot.agent.model.Profile;
import org.glowroot.agent.model.QueryData;
import org.glowroot.agent.model.ThreadStats;
import org.glowroot.agent.model.TimerImpl;
import org.glowroot.agent.model.Transaction;
import org.glowroot.common.config.AdvancedConfig;
import org.glowroot.common.model.LazyHistogram;
import org.glowroot.common.model.LazyHistogram.ScratchBuffer;
import org.glowroot.common.model.MutableProfile;
import org.glowroot.common.model.QueryCollector;
import org.glowroot.common.util.NotAvailableAware;
import org.glowroot.common.util.Styles;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.wire.api.model.AggregateOuterClass.Aggregate.OptionalDouble;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

// must be used under an appropriate lock
@Styles.Private
class AggregateCollector {

    private final @Nullable String transactionName;
    private long totalNanos;
    private long transactionCount;
    private long errorCount;
    private final List<MutableTimer> mainThreadRootTimers = Lists.newArrayList();
    private final List<MutableTimer> auxThreadRootTimers = Lists.newArrayList();
    private final List<MutableTimer> asyncRootTimers = Lists.newArrayList();
    private final MutableThreadStats mainThreadStats = new MutableThreadStats();
    private final MutableThreadStats auxThreadStats = new MutableThreadStats();
    // histogram values are in nanoseconds, but with microsecond precision to reduce the number of
    // buckets (and memory) required
    private final LazyHistogram lazyHistogram = new LazyHistogram();
    // TODO lazy instantiate mutable profiles to reduce memory footprint (same as MutableAggregate)
    private final MutableProfile mainThreadProfile = new MutableProfile();
    private final MutableProfile auxThreadProfile = new MutableProfile();
    private final QueryCollector queries;

    AggregateCollector(@Nullable String transactionName, int maxAggregateQueriesPerQueryType) {
        int hardLimitMultiplierWhileBuilding = transactionName == null
                ? AdvancedConfig.OVERALL_AGGREGATE_QUERIES_HARD_LIMIT_MULTIPLIER
                : AdvancedConfig.TRANSACTION_AGGREGATE_QUERIES_HARD_LIMIT_MULTIPLIER;
        queries = new QueryCollector(maxAggregateQueriesPerQueryType,
                hardLimitMultiplierWhileBuilding);
        this.transactionName = transactionName;
    }

    void add(Transaction transaction) {
        long totalNanos = transaction.getDurationNanos();
        this.totalNanos += totalNanos;
        transactionCount++;
        if (transaction.getErrorMessage() != null) {
            errorCount++;
        }
        ThreadStats mainThreadStats = transaction.getMainThreadStats();
        if (mainThreadStats != null) {
            if (transaction.isAsynchronous()) {
                // the main thread is treated as just another auxiliary thread
                this.auxThreadStats.addThreadStats(mainThreadStats);
            } else {
                this.mainThreadStats.addThreadStats(mainThreadStats);
            }
        }
        for (ThreadStats auxThreadStats : transaction.getAuxThreadStats()) {
            this.auxThreadStats.addThreadStats(auxThreadStats);
        }
        lazyHistogram.add(totalNanos);
    }

    void mergeMainThreadRootTimer(TimerImpl toBeMergedRootTimer) {
        mergeRootTimer(toBeMergedRootTimer, mainThreadRootTimers);
    }

    void mergeAuxThreadRootTimer(TimerImpl toBeMergedRootTimer) {
        mergeRootTimer(toBeMergedRootTimer, auxThreadRootTimers);
    }

    void mergeAsyncRootTimer(CommonTimerImpl toBeMergedRootTimer) {
        mergeRootTimer(toBeMergedRootTimer, asyncRootTimers);
    }

    void mergeMainThreadProfile(Profile toBeMergedProfile) {
        toBeMergedProfile.mergeIntoProfile(mainThreadProfile);
    }

    void mergeAuxThreadProfile(Profile toBeMergedProfile) {
        toBeMergedProfile.mergeIntoProfile(auxThreadProfile);
    }

    void mergeQueries(Iterator<QueryData> toBeMergedQueries) {
        while (toBeMergedQueries.hasNext()) {
            QueryData toBeMergedQuery = toBeMergedQueries.next();
            queries.mergeQuery(toBeMergedQuery.getQueryType(), toBeMergedQuery.getQueryText(),
                    toBeMergedQuery.getTotalNanos(), toBeMergedQuery.getExecutionCount(),
                    toBeMergedQuery.getTotalRows());
        }
    }

    Aggregate build(ScratchBuffer scratchBuffer) throws IOException {
        Aggregate.Builder builder = Aggregate.newBuilder()
                .setTotalDurationNanos(totalNanos)
                .setTransactionCount(transactionCount)
                .setErrorCount(errorCount)
                .addAllMainThreadRootTimer(getRootTimersProtobuf(mainThreadRootTimers))
                .addAllAuxThreadRootTimer(getRootTimersProtobuf(auxThreadRootTimers))
                .addAllAsyncRootTimer(getRootTimersProtobuf(asyncRootTimers))
                .setTotalNanosHistogram(lazyHistogram.toProtobuf(scratchBuffer));
        if (!mainThreadStats.isEmpty()) {
            builder.setMainThreadStats(mainThreadStats.toProto());
        }
        if (!auxThreadStats.isEmpty()) {
            builder.setAuxThreadStats(auxThreadStats.toProto());
        }
        if (mainThreadProfile.getSampleCount() > 0) {
            builder.setMainThreadProfile(mainThreadProfile.toProtobuf());
        }
        if (auxThreadProfile.getSampleCount() > 0) {
            builder.setAuxThreadProfile(auxThreadProfile.toProtobuf());
        }
        return builder.addAllQueriesByType(queries.toProtobuf(true))
                .build();
    }

    private static void mergeRootTimer(CommonTimerImpl toBeMergedRootTimer,
            List<MutableTimer> rootTimers) {
        for (MutableTimer rootTimer : rootTimers) {
            if (toBeMergedRootTimer.getName().equals(rootTimer.getName())) {
                rootTimer.merge(toBeMergedRootTimer);
                return;
            }
        }
        MutableTimer rootTimer = MutableTimer.createRootTimer(toBeMergedRootTimer.getName(),
                toBeMergedRootTimer.isExtended());
        rootTimer.merge(toBeMergedRootTimer);
        rootTimers.add(rootTimer);
    }

    private static List<Aggregate.Timer> getRootTimersProtobuf(List<MutableTimer> rootTimers) {
        List<Aggregate.Timer> protobufRootTimers =
                Lists.newArrayListWithCapacity(rootTimers.size());
        for (MutableTimer rootTimer : rootTimers) {
            protobufRootTimers.add(rootTimer.toProtobuf());
        }
        return protobufRootTimers;
    }

    private static class MutableThreadStats {

        private double totalCpuNanos = NotAvailableAware.NA;
        private double totalBlockedNanos = NotAvailableAware.NA;
        private double totalWaitedNanos = NotAvailableAware.NA;
        private double totalAllocatedBytes = NotAvailableAware.NA;

        private void addThreadStats(ThreadStats threadStats) {
            totalCpuNanos = NotAvailableAware.add(totalCpuNanos, threadStats.getTotalCpuNanos());
            long totalBlockedMillis = threadStats.getTotalBlockedMillis();
            if (!NotAvailableAware.isNA(totalBlockedMillis)) {
                totalBlockedNanos = NotAvailableAware.add(totalBlockedNanos,
                        MILLISECONDS.toNanos(totalBlockedMillis));
            }
            long totalWaitedMillis = threadStats.getTotalWaitedMillis();
            if (!NotAvailableAware.isNA(totalWaitedMillis)) {
                totalWaitedNanos = NotAvailableAware.add(totalWaitedNanos,
                        MILLISECONDS.toNanos(totalWaitedMillis));
            }
            totalAllocatedBytes = NotAvailableAware.add(totalAllocatedBytes,
                    threadStats.getTotalAllocatedBytes());
        }

        private boolean isEmpty() {
            return NotAvailableAware.isNA(totalCpuNanos)
                    && NotAvailableAware.isNA(totalBlockedNanos)
                    && NotAvailableAware.isNA(totalWaitedNanos)
                    && NotAvailableAware.isNA(totalAllocatedBytes);
        }

        public Aggregate.ThreadStats toProto() {
            return Aggregate.ThreadStats.newBuilder()
                    .setTotalCpuNanos(toOptionalDouble(totalCpuNanos))
                    .setTotalBlockedNanos(toOptionalDouble(totalBlockedNanos))
                    .setTotalWaitedNanos(toOptionalDouble(totalWaitedNanos))
                    .setTotalAllocatedBytes(toOptionalDouble(totalAllocatedBytes))
                    .build();
        }

        private static OptionalDouble toOptionalDouble(double value) {
            if (NotAvailableAware.isNA(value)) {
                return OptionalDouble.getDefaultInstance();
            } else {
                return OptionalDouble.newBuilder().setValue(value).build();
            }
        }
    }
}
