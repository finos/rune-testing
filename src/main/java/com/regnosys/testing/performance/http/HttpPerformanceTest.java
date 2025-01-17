package com.regnosys.testing.performance.http;

/*-
 * ===============
 * Rune Testing
 * ===============
 * Copyright (C) 2022 - 2025 REGnosys
 * ===============
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
 * ===============
 */

import com.regnosys.testing.performance.PerformanceTest;
import com.regnosys.testing.performance.PerformanceTestHarness;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class HttpPerformanceTest implements PerformanceTest<byte[], byte[]> {

    private final HttpClient client;
    private final String apiUrl;
    private final String inputFilesDir;
    private final String ext;

    public HttpPerformanceTest(HttpClient client, String apiUrl, String inputFilesDir, String ext) {
        this.client = client == null ? HttpClient.newBuilder().build() : client;
        this.apiUrl = Objects.requireNonNull(apiUrl);
        this.inputFilesDir = Objects.requireNonNull(inputFilesDir);
        this.ext = Objects.requireNonNull(ext);
    }

    @Override
    public void initState() throws Exception {
    }

    @Override
    public List<byte[]> loadData() throws Exception {
        return Files.walk(Paths.get(inputFilesDir))
                .filter(x -> x.toString().endsWith("." + ext))
                .map(HttpPerformanceTest::readAllBytes)
                .collect(Collectors.toList());
    }

    @Override
    public byte[] run(byte[] data) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(data)).build();
        HttpResponse<?> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Did not get 200 response: " + response.body());
        }
        return (byte[]) response.body();
    }

    private static byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void main(String[] args) {
        int threads = Integer.parseInt(System.getProperty("threads", "4"));
        int runs = Integer.parseInt(System.getProperty("runs", "4"));
        String api_url = System.getProperty("apiUrl");
        String input_files_dir = System.getProperty("inputFilesDir");
        String ext = System.getProperty("ext", "json");

        PerformanceTestHarness.execute(threads, runs, new HttpPerformanceTest(null, api_url, input_files_dir, ext));
        System.exit(0);
    }
}
