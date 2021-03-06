/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.api.index.sampling;

import java.util.function.LongPredicate;

import org.neo4j.common.TokenNameLookup;
import org.neo4j.internal.schema.IndexDescriptor;
import org.neo4j.kernel.impl.api.index.IndexMapSnapshotProvider;
import org.neo4j.kernel.impl.api.index.IndexSamplingConfig;
import org.neo4j.kernel.impl.api.index.stats.IndexStatisticsStore;
import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;
import org.neo4j.register.Register.DoubleLongRegister;
import org.neo4j.scheduler.JobScheduler;

import static org.neo4j.register.Registers.newDoubleLongRegister;

public class IndexSamplingControllerFactory
{
    private final IndexSamplingConfig config;
    private final IndexStatisticsStore indexStatisticsStore;
    private final JobScheduler scheduler;
    private final TokenNameLookup tokenNameLookup;
    private final LogProvider logProvider;

    public IndexSamplingControllerFactory( IndexSamplingConfig config, IndexStatisticsStore indexStatisticsStore,
                                           JobScheduler scheduler, TokenNameLookup tokenNameLookup,
                                           LogProvider logProvider )
    {
        this.config = config;
        this.indexStatisticsStore = indexStatisticsStore;
        this.scheduler = scheduler;
        this.tokenNameLookup = tokenNameLookup;
        this.logProvider = logProvider;
    }

    public IndexSamplingController create( IndexMapSnapshotProvider snapshotProvider )
    {
        OnlineIndexSamplingJobFactory jobFactory = new OnlineIndexSamplingJobFactory( indexStatisticsStore, tokenNameLookup, logProvider );
        LongPredicate samplingUpdatePredicate = createSamplingPredicate();
        IndexSamplingJobTracker jobTracker = new IndexSamplingJobTracker( config, scheduler );
        RecoveryCondition indexRecoveryCondition = createIndexRecoveryCondition( logProvider, tokenNameLookup );
        return new IndexSamplingController(
                config, jobFactory, samplingUpdatePredicate, jobTracker, snapshotProvider, scheduler, indexRecoveryCondition,
                logProvider );
    }

    private LongPredicate createSamplingPredicate()
    {
        return indexId -> {
            DoubleLongRegister output = newDoubleLongRegister();
            indexStatisticsStore.indexUpdatesAndSize( indexId, output );
            long updates = output.readFirst();
            long size = output.readSecond();
            long threshold = Math.round( config.updateRatio() * size );
            return updates > threshold;
        };
    }

    private RecoveryCondition createIndexRecoveryCondition( final LogProvider logProvider,
                                                                     final TokenNameLookup tokenNameLookup )
    {
        return new RecoveryCondition()
        {
            private final Log log = logProvider.getLog( IndexSamplingController.class );
            private final DoubleLongRegister register = newDoubleLongRegister();

            @Override
            public boolean test( IndexDescriptor descriptor )
            {
                final long samples = indexStatisticsStore.indexSample( descriptor.getId(), register ).readSecond();
                final long size = indexStatisticsStore.indexUpdatesAndSize( descriptor.getId(), register ).readSecond();
                final boolean result = samples == 0 || size == 0;
                if ( result )
                {
                    log.debug( "Recovering index sampling for index %s", descriptor.schema().userDescription( tokenNameLookup ) );
                }
                return result;
            }
        };
    }
}
