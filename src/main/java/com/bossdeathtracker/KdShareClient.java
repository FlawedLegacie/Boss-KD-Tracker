package com.bossdeathtracker;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.function.Consumer;
import javax.inject.Inject;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Optional transport used to make !KD work like RuneLite's !task command.
 *
 * All network work is asynchronous and only runs when the user explicitly
 * enables chat sharing and supplies an HTTPS service URL.
 */
final class KdShareClient
{
    private final OkHttpClient httpClient;
    private final Gson gson;

    @Inject
    KdShareClient(OkHttpClient httpClient, Gson gson)
    {
        this.httpClient = httpClient;
        this.gson = gson;
    }

    boolean isValidBaseUrl(String value)
    {
        return parseBaseUrl(value) != null;
    }

    void submit(
        String baseUrl,
        KdSharePayload payload,
        Consumer<Boolean> completion)
    {
        HttpUrl endpoint = endpoint(baseUrl);
        if (endpoint == null || payload == null || !payload.isValid())
        {
            completion.accept(false);
            return;
        }

        Request request = new Request.Builder()
            .url(endpoint)
            .post(RequestBody.create(RuneLiteAPI.JSON, gson.toJson(payload)))
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException exception)
            {
                completion.accept(false);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response ignored = response)
                {
                    completion.accept(response.isSuccessful());
                }
            }
        });
    }

    void lookup(
        String baseUrl,
        String playerName,
        String query,
        Consumer<KdSharePayload> completion)
    {
        HttpUrl endpoint = endpoint(baseUrl);
        String cleanPlayer = playerName == null ? "" : playerName.trim();
        String cleanQuery = KdSharePayload.normalizeQuery(query);

        if (endpoint == null || cleanPlayer.isEmpty() || cleanQuery.isEmpty())
        {
            completion.accept(null);
            return;
        }

        HttpUrl url = endpoint.newBuilder()
            .addQueryParameter("name", cleanPlayer)
            .addQueryParameter("query", cleanQuery)
            .build();

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException exception)
            {
                completion.accept(null);
            }

            @Override
            public void onResponse(Call call, Response response)
            {
                try (Response ignored = response)
                {
                    if (!response.isSuccessful() || response.body() == null)
                    {
                        completion.accept(null);
                        return;
                    }

                    KdSharePayload payload;
                    try
                    {
                        payload = gson.fromJson(response.body().charStream(), KdSharePayload.class);
                    }
                    catch (RuntimeException exception)
                    {
                        completion.accept(null);
                        return;
                    }

                    if (payload == null
                        || !payload.isValid()
                        || !payload.matchesQuery(cleanQuery)
                        || !payload.getName().equalsIgnoreCase(cleanPlayer))
                    {
                        completion.accept(null);
                        return;
                    }

                    completion.accept(payload);
                }
            }
        });
    }

    private static HttpUrl endpoint(String baseUrl)
    {
        HttpUrl base = parseBaseUrl(baseUrl);
        if (base == null)
        {
            return null;
        }

        return base.newBuilder()
            .addPathSegment("v1")
            .addPathSegment("kd")
            .build();
    }

    private static HttpUrl parseBaseUrl(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }

        final HttpUrl url;
        try
        {
            url = HttpUrl.get(value.trim());
        }
        catch (IllegalArgumentException exception)
        {
            return null;
        }

        return "https".equalsIgnoreCase(url.scheme()) ? url : null;
    }
}
