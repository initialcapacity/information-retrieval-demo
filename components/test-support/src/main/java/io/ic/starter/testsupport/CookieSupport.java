package io.ic.starter.testsupport;

import io.ic.starter.websupport.cookies.Signer;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Optional;

public class CookieSupport {

    public static Optional<String> parseCookie(HttpResponse<?> response, String name) {
        var cookiePrefix = name + "=";
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(cookiePrefix))
                .findFirst()
                .flatMap(cookie -> Arrays.stream(cookie.substring(cookiePrefix.length()).split(";")).findFirst());
    }

    public static HttpRequest.Builder requestWithCookie(URI uri, String cookieHeader) {
        return HttpRequest.newBuilder(uri).header("Cookie", cookieHeader);
    }

    public static String cookie(String name, String value) {
        return name + "=" + value + ";";
    }

    public static Signer cookieSigner() {
        var env = TestEnvironment.create("not_used");
        return new Signer(env.cookieSecret());
    }
}
