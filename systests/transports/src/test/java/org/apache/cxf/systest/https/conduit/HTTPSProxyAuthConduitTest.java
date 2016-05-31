/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.systest.https.conduit;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.cxf.configuration.security.ProxyAuthorizationPolicy;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import org.littleshoot.proxy.HttpFilters;
import org.littleshoot.proxy.HttpFiltersAdapter;
import org.littleshoot.proxy.HttpFiltersSourceAdapter;
import org.littleshoot.proxy.HttpProxyServer;
import org.littleshoot.proxy.ProxyAuthenticator;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;

import io.netty.handler.codec.http.HttpRequest;

/**
 * 
 */
public class HTTPSProxyAuthConduitTest extends HTTPSConduitTest {
    static final int PROXY_PORT = Integer.parseInt(allocatePort(HTTPSProxyAuthConduitTest.class));
    static HttpProxyServer proxy;

    private static AtomicInteger count = new AtomicInteger();
    static class CountingFilter extends HttpFiltersAdapter {

        CountingFilter(HttpRequest originalRequest) {
            super(originalRequest);
        }

        @Override
        public void proxyToServerRequestSent() {
            count.incrementAndGet();
        }
    }

    public HTTPSProxyAuthConduitTest() {
    }

    @AfterClass
    public static void stopProxy() {
        proxy.stop();
        proxy = null;
    }

    @BeforeClass
    public static void startProxy() {
        proxy = DefaultHttpProxyServer.bootstrap().withPort(PROXY_PORT)
                .withFiltersSource(new HttpFiltersSourceAdapter() {
                    @Override
                    public HttpFilters filterRequest(HttpRequest originalRequest) {
                        return new CountingFilter(originalRequest);
                    }
                })
                .withProxyAuthenticator(new ProxyAuthenticator() {
                    @Override
                    public boolean authenticate(String userName, String password) {
                        return "password".equals(password) && "CXF".equals(userName);
                    }
                }).start();
    }

    @Before
    public void resetCount() {
        count.set(0);
    }
    
    public void configureProxy(Client client) {
        HTTPConduit cond = (HTTPConduit)client.getConduit();
        HTTPClientPolicy pol = cond.getClient();
        if (pol == null) {
            pol = new HTTPClientPolicy();
            cond.setClient(pol);
        }
        pol.setProxyServer("localhost");
        pol.setProxyServerPort(PROXY_PORT);
        ProxyAuthorizationPolicy auth = new ProxyAuthorizationPolicy();
        auth.setUserName("CXF");
        auth.setPassword("password");
        cond.setProxyAuthorization(auth);
    }
    
    public void resetProxyCount() {
        count.set(0);
    }
    public void assertProxyRequestCount(int i) {
        assertEquals("Unexpected request count", i, count.get());
    }
    
}
