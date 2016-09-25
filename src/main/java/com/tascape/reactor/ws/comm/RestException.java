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
package com.tascape.reactor.ws.comm;

import java.io.IOException;

/**
 *
 * @author linsong wang
 */
public class RestException extends IOException {
    private static final long serialVersionUID = 1L;

    public static final HttpCodeMatcher HTTP_400 = new HttpCodeMatcher(400);

    public static final HttpCodeMatcher HTTP_403 = new HttpCodeMatcher(403);

    public static final HttpCodeMatcher HTTP_404 = new HttpCodeMatcher(404);

    private final int httpCode;

    /**
     * @param httpCode HTTP response code
     * @param message  error message
     */
    public RestException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    /**
     *
     * @return HTTP response code
     */
    public int getHttpCode() {
        return httpCode;
    }
}
