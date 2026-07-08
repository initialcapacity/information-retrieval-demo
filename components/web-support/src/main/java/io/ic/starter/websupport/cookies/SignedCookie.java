package io.ic.starter.websupport.cookies;

import io.javalin.http.Context;

import java.util.Optional;

public class SignedCookie {
    private final String name;
    private final Signer signer;

    public SignedCookie(String name, Signer signer) {
        this.name = name;
        this.signer = signer;
    }

    @SuppressWarnings("UnusedReturnValue")
    public Context set(Context ctx, String value) {
        return ctx.cookie(name, signer.sign(value));
    }

    public Optional<String> get(Context ctx) {
        String rawValue = ctx.cookie(name);
        if (rawValue == null || rawValue.isEmpty()) {
            return Optional.empty();
        }

        return signer.validate(rawValue);
    }

    @SuppressWarnings("UnusedReturnValue")
    public Context clear(Context ctx) {
        return ctx.removeCookie(name);
    }
}
