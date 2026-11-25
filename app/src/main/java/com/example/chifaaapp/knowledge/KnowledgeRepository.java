package com.example.chifaaapp.knowledge;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Response;

final class KnowledgeRepository {
    static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final KnowledgeApi api;
    private final VisitedCache cache;
    volatile boolean revoked;
    private CompletableFuture<Void> refresh;

    KnowledgeRepository(KnowledgeApi api, VisitedCache cache) {
        this.api = api;
        this.cache = cache;
    }

    static final class Result<T> {
        final T value;
        final boolean cached;
        Result(T value, boolean cached) { this.value = value; this.cached = cached; }
    }

    static final class Failure extends IOException {
        final int code;
        Failure(int code) { this.code = code; }
    }

    void clear() { cache.clear(); }

    private static <T> Response<T> fetch(Call<T> request, boolean online) throws IOException {
        if (!online) return null;
        try {
            return request.execute();
        } catch (SocketException | SocketTimeoutException | UnknownHostException unreachable) {
            return null;
        }
    }

    private void check(int code) throws Failure {
        if (revoked) throw new Failure(401);
        if (code == 401 || code == 403) {
            revoked = true;
            cache.clear();
            throw new Failure(401);
        }
        if (code != 200) throw new Failure(code);
    }

    Result<KnowledgeApi.Page> list(KnowledgeFilter filters, String after, boolean online) throws IOException {
        check(200);
        Response<KnowledgeApi.Page> response = fetch(api.list(filters.keyword, filters.topic, filters.language, after), online);
        if (response != null) {
            check(response.code());
            KnowledgeApi.Page page = response.body();
            if (page == null || page.items == null) throw new Failure(502);
            for (KnowledgeApi.Item item : page.items)
                if (item == null || !item.validSummary()) throw new Failure(502);
            return new Result<>(page, false);
        }
        KnowledgeApi.Page page = new KnowledgeApi.Page();
        page.items = new ArrayList<>();
        for (String id : cache.ids()) {
            KnowledgeApi.Item item = cache.get(id);
            if (item != null && filters.matches(item)) page.items.add(item);
        }
        return new Result<>(page, true);
    }

    Result<KnowledgeApi.Item> detail(String id, boolean online) throws IOException {
        check(200);
        Response<KnowledgeApi.Item> response = fetch(api.detail(id), online);
        if (response != null) {
            if (response.code() == 404 || response.code() == 410) cache.remove(id);
            check(response.code());
            KnowledgeApi.Item item = response.body();
            if (item == null || !id.equals(item.contentId) || !item.readable(VisitedCache.today())) {
                cache.remove(id);
                throw new Failure(404);
            }
            cache.put(item);
            return new Result<>(item, false);
        }
        KnowledgeApi.Item saved = cache.get(id);
        if (saved == null) throw new Failure(0);
        return new Result<>(saved, true);
    }

    CompletableFuture<Void> refreshVisited(String alreadyFetched) {
        if (refresh != null && !refresh.isDone()) return refresh;
        refresh = new CompletableFuture<>();
        ArrayList<String> ids = new ArrayList<>(cache.ids());
        ids.remove(alreadyFetched);
        refreshNext(ids.iterator(), refresh);
        return refresh;
    }

    // Une seule requête de synchronisation en file à la fois : une ouverture de fiche peut passer entre deux.
    private void refreshNext(Iterator<String> ids, CompletableFuture<Void> done) {
        if (revoked || !ids.hasNext()) {
            done.complete(null);
            return;
        }
        IO.execute(() -> {
            try {
                if (detail(ids.next(), true).cached) { done.complete(null); return; }
            } catch (Failure e) {
                if (e.code != 404 && e.code != 410) { done.completeExceptionally(e); return; }
            } catch (IOException | RuntimeException e) {
                done.completeExceptionally(e);
                return;
            }
            refreshNext(ids, done);
        });
    }
}
