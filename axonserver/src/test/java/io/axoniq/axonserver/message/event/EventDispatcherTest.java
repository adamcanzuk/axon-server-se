/*
 * Copyright (c) 2017-2019 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.message.event;

import io.axoniq.axonserver.applicationevents.TopologyEvents;
import io.axoniq.axonserver.grpc.event.Confirmation;
import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.grpc.event.GetAggregateEventsRequest;
import io.axoniq.axonserver.grpc.event.GetEventsRequest;
import io.axoniq.axonserver.metric.DefaultMetricCollector;
import io.axoniq.axonserver.metric.MeterFactory;
import io.axoniq.axonserver.topology.EventStoreLocator;
import io.axoniq.axonserver.topology.Topology;
import io.axoniq.axonserver.util.CountingStreamObserver;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.Metrics;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

/**
 * @author Marc Gathier
 */
@RunWith(MockitoJUnitRunner.class)
public class EventDispatcherTest {
    private EventDispatcher testSubject;
    @Mock
    private EventStore eventStoreClient;

    @Mock
    private EventStoreLocator eventStoreLocator;

    @Mock
    private StreamObserver<InputStream> appendEventConnection;

    @Before
    public void setUp() {
        when(eventStoreClient.createAppendEventConnection(any(), any())).thenReturn(appendEventConnection);
        when(eventStoreLocator.getEventStore(eq("OtherContext"))).thenReturn(null);
        when(eventStoreLocator.getEventStore(eq(Topology.DEFAULT_CONTEXT))).thenReturn(eventStoreClient);
        testSubject = new EventDispatcher(eventStoreLocator, () -> Topology.DEFAULT_CONTEXT,
                                          new MeterFactory(Metrics.globalRegistry,
                                          new DefaultMetricCollector()));
    }

    @Test
    public void appendEvent() {
        CountingStreamObserver<Confirmation> responseObserver = new CountingStreamObserver<>();
        StreamObserver<InputStream> inputStream = testSubject.appendEvent(responseObserver);
        inputStream.onNext(dummyEvent());
        assertEquals(0, responseObserver.count);
        inputStream.onCompleted();
        verify( appendEventConnection).onCompleted();
    }

    private InputStream dummyEvent() {
        return new ByteArrayInputStream(Event.newBuilder().build().toByteArray());
    }

    @Test
    public void appendEventRollback() {
        CountingStreamObserver<Confirmation> responseObserver = new CountingStreamObserver<>();
        StreamObserver<InputStream> inputStream = testSubject.appendEvent(responseObserver);
        inputStream.onNext(dummyEvent());
        assertEquals(0, responseObserver.count);
        inputStream.onError(new Throwable());
        assertNull(responseObserver.error);
        verify( appendEventConnection).onError(anyObject());
    }

    @Test
    public void appendSnapshot() {
        CountingStreamObserver<Confirmation> responseObserver = new CountingStreamObserver<>();
        CompletableFuture<Confirmation> appendFuture = new CompletableFuture<>();
        when(eventStoreClient.appendSnapshot(any(), any(Event.class))).thenReturn(appendFuture);
        testSubject.appendSnapshot(Event.newBuilder().build(), responseObserver);
        appendFuture.complete(Confirmation.newBuilder().build());
        verify(eventStoreClient).appendSnapshot(any(), any(Event.class));
        assertEquals(1, responseObserver.count);
    }

    @Test
    public void listAggregateEventsNoEventStore() {
        testSubject.listAggregateEvents("OtherContext", GetAggregateEventsRequest.newBuilder().build(), new CountingStreamObserver<>());
    }

    @Test
    public void listEvents() {
        CountingStreamObserver<InputStream> responseObserver = new CountingStreamObserver<>();
        AtomicReference<StreamObserver<InputStream>> eventStoreOutputStreamRef = new AtomicReference<>();
        StreamObserver<GetEventsRequest> eventStoreResponseStream = new StreamObserver<GetEventsRequest>() {
            @Override
            public void onNext(GetEventsRequest o) {
                StreamObserver<InputStream> responseStream = eventStoreOutputStreamRef.get();
                responseStream.onNext(new ByteArrayInputStream(EventWithToken.newBuilder().build().toByteArray()));
                responseStream.onCompleted();
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onCompleted() {

            }
        };
        when(eventStoreClient.listEvents(any(), any(StreamObserver.class))).then(a -> {
            eventStoreOutputStreamRef.set((StreamObserver<InputStream>) a.getArguments()[1]);
            return eventStoreResponseStream;
        });
        StreamObserver<GetEventsRequest> inputStream = testSubject.listEvents(responseObserver);
        inputStream.onNext(GetEventsRequest.newBuilder().setClientId("sampleClient").build());
        assertEquals(1, responseObserver.count);
        assertTrue(responseObserver.completed);
        testSubject.on(new TopologyEvents.ApplicationDisconnected(Topology.DEFAULT_CONTEXT, "myComponent", "sampleClient"));
        assertTrue(testSubject.eventTrackerStatus(Topology.DEFAULT_CONTEXT).isEmpty());
    }


}
