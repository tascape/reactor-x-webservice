/*
 * Copyright 2015 - 2016 Nebula Bay.
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
package com.tascape.reactor.ws.comm;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author linsong wang
 */
public class HttpCodeMatcher extends BaseMatcher<WebServiceException> {
    private static final Logger LOG = LoggerFactory.getLogger(HttpCodeMatcher.class);

    private final int httpCode;

    /**
     * @param httpCode HTTP response code
     */
    public HttpCodeMatcher(int httpCode) {
        this.httpCode = httpCode;
    }

    @Override
    public boolean matches(Object item) {
        LOG.info("Try to match HTTP code {}.", this.httpCode);
        if (item instanceof WebServiceException) {
            return ((WebServiceException) item).getHttpCode() == httpCode;
        }
        return false;
    }

    @Override
    public void describeTo(Description description) {
    }
}
