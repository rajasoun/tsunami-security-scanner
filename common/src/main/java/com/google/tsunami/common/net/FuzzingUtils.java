/*
 * Copyright 2022 Google LLC
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
package com.google.tsunami.common.net;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.joining;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.tsunami.common.net.http.HttpRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Fuzzing utilities for HTTP request properties. */
public final class FuzzingUtils {
  /* TODO(b/251480660): Refactor to generic fuzzing library. */

  /**
   * Fuzz GET parameters by replacing values with the provided payload. If no GET parameter is
   * found, add a new parameter called {@code defaultParameter}.
   */
  public static ImmutableList<HttpRequest> fuzzGetParametersWithDefaultParameter(
      HttpRequest request, String payload, String defaultParameter) {
    return fuzzGetParameters(request, payload, Optional.of(defaultParameter));
  }

  /**
   * Fuzz GET parameters by replacing values with the provided payload. If no GET parameter is
   * found, return an empty list.
   */
  public static ImmutableList<HttpRequest> fuzzGetParameters(HttpRequest request, String payload) {
    return fuzzGetParameters(request, payload, Optional.empty());
  }

  private static ImmutableList<HttpRequest> fuzzGetParameters(
      HttpRequest request, String payload, Optional<String> defaultParameter) {
    URI parsedUrl = URI.create(request.url());
    ImmutableList<HttpQueryParameter> queryParams = parseQuery(parsedUrl.getQuery());
    if (queryParams.isEmpty() && defaultParameter.isPresent()) {
      return ImmutableList.of(
          request.toBuilder()
              .setUrl(
                  assembleUrlWithQueries(
                      parsedUrl,
                      ImmutableList.of(HttpQueryParameter.create(defaultParameter.get(), payload))))
              .build());
    }
    return fuzzParams(queryParams, payload).stream()
        .map(fuzzedParams -> assembleUrlWithQueries(parsedUrl, fuzzedParams))
        .map(fuzzedUrl -> request.toBuilder().setUrl(fuzzedUrl).build())
        .collect(toImmutableList());
  }

  private static ImmutableSet<ImmutableList<HttpQueryParameter>> fuzzParams(
      ImmutableList<HttpQueryParameter> params, String payload) {
    ImmutableSet.Builder<ImmutableList<HttpQueryParameter>> fuzzedParamsbuilder =
        ImmutableSet.builder();

    for (int i = 0; i < params.size(); i++) {
      List<HttpQueryParameter> paramsWithPayload = new ArrayList<>(params);
      paramsWithPayload.set(i, HttpQueryParameter.create(params.get(i).name(), payload));
      fuzzedParamsbuilder.add(ImmutableList.copyOf(paramsWithPayload));
    }

    return fuzzedParamsbuilder.build();
  }

  public static ImmutableList<HttpQueryParameter> parseQuery(String query) {
    if (isNullOrEmpty(query)) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<HttpQueryParameter> queryParamsBuilder = ImmutableList.builder();
    for (String param : Splitter.on('&').split(query)) {
      int equalPosition = param.indexOf("=");
      if (equalPosition > -1) {
        String name = param.substring(0, equalPosition);
        String value = param.substring(equalPosition + 1);
        queryParamsBuilder.add(HttpQueryParameter.create(name, value));
      } else {
        queryParamsBuilder.add(HttpQueryParameter.create(param, ""));
      }
    }
    return queryParamsBuilder.build();
  }

  private static String assembleUrlWithQueries(
      URI parsedUrl, ImmutableList<HttpQueryParameter> params) {
    String query = assembleQueryParams(params);
    StringBuilder urlBuilder = new StringBuilder();
    urlBuilder.append(parsedUrl.getScheme()).append("://").append(parsedUrl.getRawAuthority());
    if (!isNullOrEmpty(parsedUrl.getRawPath())) {
      urlBuilder.append(parsedUrl.getRawPath());
    }
    if (!isNullOrEmpty(query)) {
      urlBuilder.append('?').append(query);
    }
    if (!isNullOrEmpty(parsedUrl.getRawFragment())) {
      urlBuilder.append('#').append(parsedUrl.getRawFragment());
    }
    return urlBuilder.toString();
  }

  private static String assembleQueryParams(ImmutableList<HttpQueryParameter> params) {
    return params.stream()
        .map(param -> String.format("%s=%s", param.name(), param.value()))
        .collect(joining("&"));
  }

  /** URL Query parameter name and value pair. */
  @AutoValue
  public abstract static class HttpQueryParameter {
    public abstract String name();

    public abstract String value();

    public static HttpQueryParameter create(String name, String value) {
      return new AutoValue_FuzzingUtils_HttpQueryParameter(name, value);
    }
  }

  private FuzzingUtils() {}
}
