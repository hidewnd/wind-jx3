package com.hidewnd.winds.scout.service.impl;

import com.hidewnd.winds.scout.model.WeiboAccount;
import com.hidewnd.winds.scout.model.WeiboCookie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 维护单个微博账号的 Cookie 状态，执行响应覆盖、删除、过期和请求匹配规则。
 */
@Component
@Slf4j
public class WeiboCookieStore {

    private static final URI WEIBO_ROOT = URI.create("https://m.weibo.cn/");

    private final Clock clock;

    public WeiboCookieStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * 使用管理端录入的 Cookie 请求头重置账号 Cookie 状态。
     *
     * @param account      微博抓取账号
     * @param cookieHeader 完整 Cookie 请求头
     */
    public void replaceSeed(WeiboAccount account, String cookieHeader) {
        account.setCookies(parseSeed(cookieHeader));
        refreshLegacyFields(account, true);
    }

    /**
     * 为目标地址生成当前有效的 Cookie 请求头。
     *
     * @param account 微博抓取账号
     * @param uri     请求地址
     * @return 匹配目标地址的 Cookie 请求头
     */
    public String buildCookieHeader(WeiboAccount account, URI uri) {
        ensureInitialized(account);
        Instant now = clock.instant();
        return account.getCookies().stream()
                .filter(cookie -> matches(cookie, uri, now))
                .sorted(Comparator.comparingInt((WeiboCookie cookie) -> cookie.getPath().length()).reversed())
                .map(cookie -> cookie.getName() + "=" + cookie.getValue())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
    }

    /**
     * 取得目标地址当前匹配的 XSRF Token。
     *
     * @param account 微博抓取账号
     * @param uri     请求地址
     * @return 当前 Cookie 状态中匹配目标地址且未过期的 Token
     */
    public String getXsrfToken(WeiboAccount account, URI uri) {
        ensureInitialized(account);
        Instant now = clock.instant();
        return account.getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()) && matches(cookie, uri, now))
                .sorted(Comparator
                        .comparingInt((WeiboCookie cookie) -> cookie.getPath().length()).reversed()
                        .thenComparing(WeiboCookie::isHostOnly, Comparator.reverseOrder()))
                .map(WeiboCookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    /**
     * 将响应中的全部 Set-Cookie 合并到账号状态。
     *
     * @param account          微博抓取账号
     * @param uri              响应对应的请求地址
     * @param setCookieHeaders 全部 Set-Cookie 响应头
     * @return Cookie 状态是否发生变化
     */
    public boolean applyResponse(
            WeiboAccount account,
            URI uri,
            List<String> setCookieHeaders) {
        ensureInitialized(account);
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return false;
        }
        boolean changed = false;
        Instant now = clock.instant();
        for (String header : setCookieHeaders) {
            try {
                for (HttpCookie responseCookie : HttpCookie.parse(header)) {
                    changed |= merge(account.getCookies(), responseCookie, uri, now);
                }
            } catch (IllegalArgumentException exception) {
                log.warn("忽略无法解析的微博 Set-Cookie，accountId={}", account.getId(), exception);
            }
        }
        if (changed) {
            refreshLegacyFields(account, true);
            account.setUpdatedAt(now);
        }
        return changed;
    }

    private List<WeiboCookie> parseSeed(String cookieHeader) {
        List<WeiboCookie> cookies = new ArrayList<>();
        if (!StringUtils.hasText(cookieHeader)) {
            return cookies;
        }
        for (String item : cookieHeader.split(";")) {
            String[] pair = item.trim().split("=", 2);
            if (pair.length == 2 && StringUtils.hasText(pair[0])) {
                cookies.add(new WeiboCookie(
                        pair[0].trim(), pair[1].trim(), WEIBO_ROOT.getHost(), "/", null,
                        true, true, false, true));
            }
        }
        return cookies;
    }

    private void ensureInitialized(WeiboAccount account) {
        if (account.getCookies() == null) {
            account.setCookies(parseSeed(account.getCookie()));
        }
    }

    private boolean merge(List<WeiboCookie> cookies, HttpCookie source, URI uri, Instant now) {
        String domain = normalizeDomain(source.getDomain() == null ? uri.getHost() : source.getDomain());
        String path = source.getPath() == null || source.getPath().isBlank()
                ? defaultPath(uri.getPath())
                : source.getPath();
        boolean hostOnly = source.getDomain() == null;
        int insertionIndex = cookies.size();
        boolean removed = false;
        for (int index = cookies.size() - 1; index >= 0; index--) {
            WeiboCookie current = cookies.get(index);
            if (current.isSeed() && current.getName().equals(source.getName())) {
                insertionIndex = index;
                cookies.remove(index);
                removed = true;
            } else if (sameKey(current, source.getName(), domain, path)) {
                insertionIndex = index;
                cookies.remove(index);
                removed = true;
            }
        }
        if (source.getMaxAge() == 0) {
            return removed;
        }
        Instant expiresAt = source.getMaxAge() > 0 ? now.plusSeconds(source.getMaxAge()) : null;
        WeiboCookie cookie = new WeiboCookie(
                source.getName(), source.getValue(), domain, path, expiresAt,
                hostOnly, source.getSecure(), source.isHttpOnly(), false);
        cookies.add(Math.min(insertionIndex, cookies.size()), cookie);
        return true;
    }

    private boolean sameKey(WeiboCookie cookie, String name, String domain, String path) {
        return cookie.getName().equals(name)
                && cookie.getDomain().equals(domain)
                && cookie.getPath().equals(path);
    }

    private boolean matches(WeiboCookie cookie, URI uri, Instant now) {
        if (cookie.getExpiresAt() != null && !cookie.getExpiresAt().isAfter(now)) {
            return false;
        }
        if (cookie.isSecure() && !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String requestHost = uri.getHost().toLowerCase(Locale.ROOT);
        boolean domainMatches = cookie.isHostOnly()
                ? requestHost.equals(cookie.getDomain())
                : requestHost.equals(cookie.getDomain()) || requestHost.endsWith("." + cookie.getDomain());
        if (!domainMatches) {
            return false;
        }
        String requestPath = uri.getPath().isEmpty() ? "/" : uri.getPath();
        String cookiePath = cookie.getPath();
        return requestPath.equals(cookiePath)
                || requestPath.startsWith(cookiePath.endsWith("/") ? cookiePath : cookiePath + "/");
    }

    private void refreshLegacyFields(WeiboAccount account, boolean clearMissingXsrf) {
        account.setCookie(buildCookieHeader(account, WEIBO_ROOT));
        String xsrfToken = account.getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName())
                        && matches(cookie, WEIBO_ROOT, clock.instant()))
                .sorted(Comparator.comparing(WeiboCookie::isHostOnly).reversed())
                .map(WeiboCookie::getValue)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        if (clearMissingXsrf || StringUtils.hasText(xsrfToken)) {
            account.setXsrfToken(xsrfToken);
        }
    }

    private String normalizeDomain(String domain) {
        String normalized = domain.toLowerCase(Locale.ROOT);
        return normalized.startsWith(".") ? normalized.substring(1) : normalized;
    }

    private String defaultPath(String requestPath) {
        int lastSlash = requestPath.lastIndexOf('/');
        return lastSlash <= 0 ? "/" : requestPath.substring(0, lastSlash);
    }
}
