/*
 * Copyright (c) 2015 - present Nebula Bay.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tascape.reactor.ws.driver;

import com.tascape.reactor.ws.comm.WebServiceCommunication;
import com.tascape.reactor.SystemConfiguration;
import com.tascape.reactor.driver.EntityDriver;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.impl.nio.DefaultHttpServerIODispatch;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.DefaultNHttpServerConnectionFactory;
import org.apache.http.impl.nio.reactor.AbstractMultiworkerIOReactor;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.NHttpConnectionFactory;
import org.apache.http.nio.protocol.HttpAsyncService;
import org.apache.http.nio.protocol.UriHttpAsyncRequestHandlerMapper;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpProcessorBuilder;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linsong wang
 */
public final class GenericWebService extends EntityDriver {
    private static final Logger LOG = LoggerFactory.getLogger(GenericWebService.class);

    public static final String SYSPROP_PORT = "reactor.driver.ws.GWS_PORT";

    public static final int XONSTANT_PORT = 10080;

    private final int port;

    private final UriHttpAsyncRequestHandlerMapper reqistry;

    private final HttpAsyncService protocolHandler;

    private ListeningIOReactor ioReactor;

    /**
     * Server port is read from system property reactor.driver.ws.GWS_PORT. Default value is 10080.
     */
    public GenericWebService() {
        this(SystemConfiguration.getInstance().getIntProperty(SYSPROP_PORT, XONSTANT_PORT));
    }

    public GenericWebService(int port) {
        this.port = port;

        LOG.info("Create HTTP protocol processing chain");
        HttpProcessor httpProcessor = HttpProcessorBuilder.create()
            .add(new ResponseDate())
            .add(new ResponseServer(GenericWebService.class.getName()))
            .add(new ResponseContent())
            .add(new ResponseConnControl()).build();

        LOG.info("Create request handler registry");
        reqistry = new UriHttpAsyncRequestHandlerMapper();
        protocolHandler = new HttpAsyncService(httpProcessor, reqistry);
    }

    public void start() throws Exception {
        LOG.info("Register shutdown handler, GET http://localhost:{}/shutdown to shutdown this service", port);
        this.registerResponseSimulator(shutdownHandler);

        LOG.info("Create server-side HTTP protocol handler");
        NHttpConnectionFactory<DefaultNHttpServerConnection> connFactory
            = new DefaultNHttpServerConnectionFactory(ConnectionConfig.DEFAULT);
        IOEventDispatch ioEventDispatch = new DefaultHttpServerIODispatch(protocolHandler, connFactory);
        IOReactorConfig config = IOReactorConfig.custom()
            .setIoThreadCount(8)
            .setSoTimeout(300)
            .setConnectTimeout(300)
            .build();

        LOG.info("Create server-side I/O reactor");
        ioReactor = new DefaultListeningIOReactor(config);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    ioReactor.shutdown(100);
                    LOG.info("Service shutdown");
                } catch (IOException ex) {
                    LOG.error("", ex);
                }
            }
        });

        try {
            SocketAddress socketAddress = new InetSocketAddress(this.port);
            ioReactor.listen(socketAddress);
            LOG.info("Service is ready");
            ioReactor.execute(ioEventDispatch);
        } catch (InterruptedIOException ex) {
            LOG.error("Interrupted", ex);
        } catch (IOException ex) {
            LOG.error("I/O error: ", ex);
        } finally {
            if (ioReactor instanceof AbstractMultiworkerIOReactor) {
                AbstractMultiworkerIOReactor amior = (AbstractMultiworkerIOReactor) ioReactor;
                amior.getAuditLog().stream().forEach(ex -> LOG.warn(ex.getTimestamp() + " - {}", ex.getCause()));
            }
        }
    }

    public void startAsync() throws Exception {
        new Thread() {
            @Override
            public void run() {
                try {
                    GenericWebService.this.start();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }.start();
    }

    public void stop() throws IOException {
        WebServiceCommunication.getUri("http://localhost:" + port + shutdownHandler.getEndpoint());
    }

    public void registerResponseSimulator(EndpointHandler simulator)
        throws InstantiationException, IllegalAccessException {
        LOG.info("Register handler for endpoint {}: {}", simulator.getEndpoint(), simulator.getName());
        reqistry.register(simulator.getEndpoint(), simulator);
    }

    public void registerResponseSimulator(String clazz)
        throws ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException,
        InvocationTargetException {
        EndpointHandler simulator = (EndpointHandler) Class.forName(clazz).getDeclaredConstructor().newInstance();
        reqistry.register(simulator.getEndpoint(), simulator);
    }

    /**
     *
     * @return the first found IP address.
     *
     * @throws SocketException if cannot get IP address
     */
    public String getIpAddress() throws SocketException {
        return NetworkInterface.getNetworkInterfaces().nextElement().getInetAddresses().nextElement().getHostAddress();
    }

    @Override
    public String getName() {
        return this.getClass().getName();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void reset() throws Exception {
        LOG.info("nothing to reset");
    }

    private final EndpointHandler shutdownHandler = new EndpointHandler() {
        @Override
        public String getEndpoint() {
            return "/shutdown";
        }

        @Override
        public void handleGet(HttpRequest request, HttpResponse response) throws HttpException, IOException {
            LOG.debug("received shutdown call");
            ioReactor.shutdown();
        }

        @Override
        public String getName() {
            return "ShutdownService";
        }

        @Override
        public void handlePost(HttpRequest request, HttpResponse response) throws HttpException, IOException {
            response.setStatusCode(HttpStatus.SC_NOT_ACCEPTABLE);
        }

        @Override
        public void handlePut(HttpRequest request, HttpResponse response) throws HttpException, IOException {
            response.setStatusCode(HttpStatus.SC_NOT_ACCEPTABLE);
        }

        @Override
        public void handleDelete(HttpRequest request, HttpResponse response) throws HttpException, IOException {
            response.setStatusCode(HttpStatus.SC_NOT_ACCEPTABLE);
        }
    };

    public static void main(String[] args) {
        GenericWebService gws = new GenericWebService();
        try {
            gws.start();
        } catch (Exception ex) {
            LOG.error("Cannot start service", ex);
        }
    }
}
