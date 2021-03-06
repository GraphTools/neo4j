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
package org.neo4j.kernel.impl.index.schema.tracking;

import java.util.concurrent.atomic.AtomicLong;

import org.neo4j.internal.schema.IndexOrder;
import org.neo4j.internal.kernel.api.IndexQuery;
import org.neo4j.internal.kernel.api.QueryContext;
import org.neo4j.internal.kernel.api.exceptions.schema.IndexNotApplicableKernelException;
import org.neo4j.kernel.api.index.IndexProgressor;
import org.neo4j.kernel.api.index.IndexReader;
import org.neo4j.kernel.api.index.IndexSampler;
import org.neo4j.storageengine.api.NodePropertyAccessor;
import org.neo4j.values.storable.Value;

public class TrackingIndexReader implements IndexReader
{
    private final IndexReader delegate;
    private final AtomicLong closeReadersCounter;

    TrackingIndexReader( IndexReader delegate, AtomicLong closeReadersCounter )
    {
        this.delegate = delegate;
        this.closeReadersCounter = closeReadersCounter;
    }

    @Override
    public long countIndexedNodes( long nodeId, int[] propertyKeyIds, Value... propertyValues )
    {
        return delegate.countIndexedNodes( nodeId, propertyKeyIds, propertyValues );
    }

    @Override
    public IndexSampler createSampler()
    {
        return delegate.createSampler();
    }

    @Override
    public void query( QueryContext context, IndexProgressor.EntityValueClient client, IndexOrder indexOrder, boolean needsValues, IndexQuery... query )
            throws IndexNotApplicableKernelException
    {
        delegate.query( context, client, indexOrder, needsValues, query );
    }

    @Override
    public boolean hasFullValuePrecision( IndexQuery... predicates )
    {
        return delegate.hasFullValuePrecision( predicates );
    }

    @Override
    public void distinctValues( IndexProgressor.EntityValueClient client, NodePropertyAccessor propertyAccessor, boolean needsValues )
    {
        delegate.distinctValues( client, propertyAccessor, needsValues );
    }

    @Override
    public void close()
    {
        delegate.close();
        closeReadersCounter.incrementAndGet();
    }
}
