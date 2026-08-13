/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.portal.webapp.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@code multipart/form-data} request body for the JDK
 * {@link java.net.http.HttpClient}, which has no built-in multipart support.
 */
public final class MultipartBodyPublisher {

    private MultipartBodyPublisher() {
    }

    /** A single form field: either a plain value, or a file (fileName + contentType set). */
    public static final class Part {

        private final String name;
        private final String value;
        private final String fileName;
        private final String contentType;
        private final byte[] fileContent;

        private Part(String name, String value, String fileName, String contentType, byte[] fileContent) {

            this.name = name;
            this.value = value;
            this.fileName = fileName;
            this.contentType = contentType;
            this.fileContent = fileContent;
        }

        public static Part ofField(String name, String value) {

            return new Part(name, value, null, null, null);
        }

        public static Part ofFile(String name, String fileName, String contentType, byte[] fileContent) {

            return new Part(name, null, fileName, contentType, fileContent);
        }
    }

    public static final class Encoded {

        private final String boundary;
        private final HttpRequest.BodyPublisher publisher;

        private Encoded(String boundary, HttpRequest.BodyPublisher publisher) {

            this.boundary = boundary;
            this.publisher = publisher;
        }

        public String contentType() {

            return "multipart/form-data; boundary=" + boundary;
        }

        public HttpRequest.BodyPublisher publisher() {

            return publisher;
        }
    }

    public static Encoded encode(List<Part> parts) throws IOException {

        String boundary = "----ComplaintPortalBoundary" + UUID.randomUUID();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Part part : parts) {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            if (part.fileContent != null) {
                out.write(("Content-Disposition: form-data; name=\"" + part.name + "\"; filename=\""
                        + headerSafe(part.fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(("Content-Type: " + headerSafe(part.contentType) + "\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(part.fileContent);
            } else {
                out.write(("Content-Disposition: form-data; name=\"" + part.name + "\"\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                out.write(part.value.getBytes(StandardCharsets.UTF_8));
            }
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return new Encoded(boundary, HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
    }

    /**
     * Strips characters that could break out of a header line or the
     * quoted-string it sits in — file names and content types originate
     * from the caller's browser and are never trustworthy as header input.
     */
    private static String headerSafe(String value) {

        return value.replaceAll("[\\r\\n\"]", "");
    }
}
