package com.hidewnd.winds.ws.auth;

import com.hidewnd.winds.scout.config.ScoutAuthorization;
import com.hidewnd.winds.scout.config.ScoutManagementTokenAuthenticator;
import org.springframework.stereotype.Component;

@Component
public class WsTokenAuthenticator {

    private final ScoutManagementTokenAuthenticator authenticator;

    public WsTokenAuthenticator(ScoutManagementTokenAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    public boolean isAuthorized(String token) {
        ScoutAuthorization result = authenticator.authorize(token);
        return result == ScoutAuthorization.AUTHORIZED || result == ScoutAuthorization.FORBIDDEN;
    }
}
