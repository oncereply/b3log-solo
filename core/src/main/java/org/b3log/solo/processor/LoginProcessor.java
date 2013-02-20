/*
 * Copyright (c) 2009, 2010, 2011, 2012, 2013, B3log Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.b3log.solo.processor;


import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.servlet.HTTPRequestContext;
import org.b3log.latke.servlet.HTTPRequestMethod;
import org.b3log.latke.servlet.annotation.RequestProcessing;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.JSONRenderer;
import org.b3log.latke.servlet.renderer.freemarker.AbstractFreeMarkerRenderer;
import org.b3log.latke.util.MD5;
import org.b3log.latke.util.Requests;
import org.b3log.latke.util.Sessions;
import org.b3log.latke.util.Strings;
import org.b3log.solo.SoloServletListener;
import org.b3log.solo.model.Common;
import org.b3log.solo.model.Preference;
import org.b3log.solo.processor.renderer.ConsoleRenderer;
import org.b3log.solo.processor.util.Filler;
import org.b3log.solo.service.PreferenceQueryService;
import org.b3log.solo.service.UserQueryService;
import org.json.JSONObject;


/**
 * Login/logout processor.
 *
 * <p>Initializes administrator</p>.
 *
 * @author <a href="mailto:DL88250@gmail.com">Liang Ding</a>
 * @author <a href="mailto:LLY219@gmail.com">Liyuan Li</a>
 * @version 1.1.1.3, Jan 18, 2013
 * @since 0.3.1
 */
@RequestProcessor
public final class LoginProcessor {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(LoginProcessor.class.getName());

    /**
     * User query service.
     */
    private static UserQueryService userQueryService = UserQueryService.getInstance();

    /**
     * Language service.
     */
    private LangPropsService langPropsService = LangPropsService.getInstance();

    /**
     * Filler.
     */
    private Filler filler = Filler.getInstance();

    /**
     * Preference query service.
     */
    private PreferenceQueryService preferenceQueryService = PreferenceQueryService.getInstance();

    /**
     * Shows login page.
     *
     * @param context the specified context
     * @throws Exception exception 
     */
    @RequestProcessing(value = { "/login"}, method = HTTPRequestMethod.GET)
    public void showLogin(final HTTPRequestContext context) throws Exception {
        final HttpServletRequest request = context.getRequest();

        String destinationURL = request.getParameter(Common.GOTO);

        if (Strings.isEmptyOrNull(destinationURL)) {
            destinationURL = "/";
        }

        final AbstractFreeMarkerRenderer renderer = new ConsoleRenderer();

        renderer.setTemplateName("login.ftl");
        context.setRenderer(renderer);

        final Map<String, Object> dataModel = renderer.getDataModel();
        final Map<String, String> langs = langPropsService.getAll(Latkes.getLocale());
        final JSONObject preference = preferenceQueryService.getPreference();

        dataModel.putAll(langs);
        dataModel.put(Common.GOTO, destinationURL);
        dataModel.put(Common.YEAR, String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
        dataModel.put(Common.VERSION, SoloServletListener.VERSION);
        dataModel.put(Common.STATIC_RESOURCE_VERSION, Latkes.getStaticResourceVersion());
        dataModel.put(Preference.BLOG_TITLE, preference.getString(Preference.BLOG_TITLE));
        dataModel.put(Preference.BLOG_HOST, preference.getString(Preference.BLOG_HOST));

        Keys.fillServer(dataModel);
        Keys.fillRuntime(dataModel);
        filler.fillMinified(dataModel);
    }

    /**
     * Logins.
     *
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "isLoggedIn": boolean,
     *     "msg": "" // optional, exists if isLoggedIn equals to false
     * }
     * </pre>
     * </p>
     *
     * @param context the specified context
     */
    @RequestProcessing(value = { "/login"}, method = HTTPRequestMethod.POST)
    public void login(final HTTPRequestContext context) {
        final HttpServletRequest request = context.getRequest();

        final JSONRenderer renderer = new JSONRenderer();

        context.setRenderer(renderer);
        final JSONObject jsonObject = new JSONObject();

        renderer.setJSONObject(jsonObject);

        try {
            jsonObject.put(Common.IS_LOGGED_IN, false);
            final String loginFailLabel = langPropsService.get("loginFailLabel");

            jsonObject.put(Keys.MSG, loginFailLabel);

            final JSONObject requestJSONObject = Requests.parseRequestJSONObject(request, context.getResponse());
            final String userEmail = requestJSONObject.getString(User.USER_EMAIL);
            final String userPwd = requestJSONObject.getString(User.USER_PASSWORD);

            if (Strings.isEmptyOrNull(userEmail) || Strings.isEmptyOrNull(userPwd)) {
                return;
            }

            LOGGER.log(Level.INFO, "Login[email={0}]", userEmail);

            final JSONObject user = userQueryService.getUserByEmail(userEmail);

            if (null == user) {
                LOGGER.log(Level.WARNING, "Not found user[email={0}]", userEmail);
                return;
            }

            if (MD5.hash(userPwd).equals(user.getString(User.USER_PASSWORD))) {
                Sessions.login(request, context.getResponse(), user);

                LOGGER.log(Level.INFO, "Logged in[email={0}]", userEmail);

                jsonObject.put(Common.IS_LOGGED_IN, true);
                jsonObject.put("to", Latkes.getServePath() + Common.ADMIN_INDEX_URI);
                jsonObject.remove(Keys.MSG);

                return;
            }

            LOGGER.log(Level.WARNING, "Wrong password[{0}]", userPwd);
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    /**
     * Logout.
     *
     * @param context the specified context
     * @throws IOException io exception
     */
    @RequestProcessing(value = { "/logout"}, method = HTTPRequestMethod.GET)
    public void logout(final HTTPRequestContext context) throws IOException {
        final HttpServletRequest httpServletRequest = context.getRequest();

        Sessions.logout(httpServletRequest, context.getResponse());

        String destinationURL = httpServletRequest.getParameter(Common.GOTO);

        if (Strings.isEmptyOrNull(destinationURL)) {
            destinationURL = "/";
        }

        context.getResponse().sendRedirect(destinationURL);
    }

    /**
     * Tries to login with cookie.
     *
     * @param request the specified request
     * @param response the specified response
     */
    public static void tryLogInWithCookie(final HttpServletRequest request, final HttpServletResponse response) {
        final Cookie[] cookies = request.getCookies();

        if (null == cookies || 0 == cookies.length) {
            return;
        }

        try {
            for (int i = 0; i < cookies.length; i++) {
                final Cookie cookie = cookies[i];

                if (!"b3log-latke".equals(cookie.getName())) {
                    continue;
                }

                final JSONObject cookieJSONObject = new JSONObject(cookie.getValue());

                final String userEmail = cookieJSONObject.optString(User.USER_EMAIL);

                if (Strings.isEmptyOrNull(userEmail)) {
                    break;
                }

                final JSONObject user = userQueryService.getUserByEmail(userEmail.toLowerCase().trim());

                if (null == user) {
                    break;
                }

                final String userPassword = user.optString(User.USER_PASSWORD);
                final String hashPassword = cookieJSONObject.optString(User.USER_PASSWORD);

                if (userPassword.equals(hashPassword)) {
                    Sessions.login(request, response, user);
                    LOGGER.log(Level.INFO, "Logged in with cookie[email={0}]", userEmail);
                }
            }
        } catch (final Exception e) {
            LOGGER.log(Level.WARNING, "Parses cookie failed, clears the cookie[name=b3log-latke]", e);

            final Cookie cookie = new Cookie("b3log-latke", null);

            cookie.setMaxAge(0);
            cookie.setPath("/");

            response.addCookie(cookie);
        }
    }
}
