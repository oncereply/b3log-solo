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
package org.b3log.solo.service;


import java.util.logging.Level;
import java.util.logging.Logger;
import org.b3log.latke.Keys;
import org.b3log.latke.model.Role;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.util.MD5;
import org.b3log.solo.model.UserExt;
import org.b3log.solo.repository.UserRepository;
import org.b3log.solo.repository.impl.UserRepositoryImpl;
import org.json.JSONObject;


/**
 * User management service.
 *
 * @author <a href="mailto:DL88250@gmail.com">Liang Ding</a>
 * @version 1.0.0.3, Jan 4, 2013
 * @since 0.4.0
 */
public final class UserMgmtService {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(UserMgmtService.class.getName());

    /**
     * User repository.
     */
    private UserRepository userRepository = UserRepositoryImpl.getInstance();

    /**
     * Language service.
     */
    private LangPropsService langPropsService = LangPropsService.getInstance();

    /**
     * Updates a user by the specified request json object.
     *
     * @param requestJSONObject the specified request json object, for example,
     * <pre>
     * {
     *     "oId": "",
     *     "userName": "",
     *     "userEmail": "",
     *     "userPassword": "", // Unhashed
     *     "userRole": ""
     * }
     * </pre>
     * @throws ServiceException service exception
     */
    public void updateUser(final JSONObject requestJSONObject) throws ServiceException {
        final Transaction transaction = userRepository.beginTransaction();

        try {
            final String oldUserId = requestJSONObject.optString(Keys.OBJECT_ID);
            final JSONObject oldUser = userRepository.get(oldUserId);

            if (null == oldUser) {
                throw new ServiceException(langPropsService.get("updateFailLabel"));
            }

            final String userNewEmail = requestJSONObject.optString(User.USER_EMAIL).toLowerCase().trim();
            // Check email is whether duplicated
            final JSONObject mayBeAnother = userRepository.getByEmail(userNewEmail);

            if (null != mayBeAnother && !mayBeAnother.optString(Keys.OBJECT_ID).equals(oldUserId)) {
                // Exists someone else has the save email as requested
                throw new ServiceException(langPropsService.get("duplicatedEmailLabel"));
            }

            // Update
            final String userName = requestJSONObject.optString(User.USER_NAME);
            final String userPassword = requestJSONObject.optString(User.USER_PASSWORD);

            oldUser.put(User.USER_EMAIL, userNewEmail);
            oldUser.put(User.USER_NAME, userName);
            oldUser.put(User.USER_PASSWORD, MD5.hash(userPassword));
            // Unchanges the default role

            userRepository.update(oldUserId, oldUser);
            transaction.commit();
        } catch (final RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.SEVERE, "Updates a user failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * Adds a user with the specified request json object.
     * 
     * @param requestJSONObject the specified request json object, for example,
     * <pre>
     * {
     *     "userName": "",
     *     "userEmail": "",
     *     "userPassword": "", // Unhashed
     *     "userRole": "" // optional, uses {@value Role#DEFAULT_ROLE} instead,
     *                       if not speciffied
     * }
     * </pre>,see {@link User} for more details
     * @return generated user id
     * @throws ServiceException service exception
     */
    public String addUser(final JSONObject requestJSONObject) throws ServiceException {
        final Transaction transaction = userRepository.beginTransaction();

        try {
            final JSONObject user = new JSONObject();
            final String userEmail = requestJSONObject.optString(User.USER_EMAIL).trim().toLowerCase();
            final JSONObject duplicatedUser = userRepository.getByEmail(userEmail);

            if (null != duplicatedUser) {
                if (transaction.isActive()) {
                    transaction.rollback();
                }

                throw new ServiceException(langPropsService.get("duplicatedEmailLabel"));
            }

            final String userName = requestJSONObject.optString(User.USER_NAME);

            user.put(User.USER_EMAIL, userEmail);
            user.put(User.USER_NAME, userName);
            final String userPassword = requestJSONObject.optString(User.USER_PASSWORD);

            user.put(User.USER_PASSWORD, MD5.hash(userPassword));
            final String roleName = requestJSONObject.optString(User.USER_ROLE, Role.DEFAULT_ROLE);

            user.put(User.USER_ROLE, roleName);
            user.put(UserExt.USER_ARTICLE_COUNT, 0);
            user.put(UserExt.USER_PUBLISHED_ARTICLE_COUNT, 0);

            userRepository.add(user);

            transaction.commit();

            return user.optString(Keys.OBJECT_ID);
        } catch (final RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.SEVERE, "Adds a user failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * Removes a user specified by the given user id.
     *
     * @param userId the given user id
     * @throws ServiceException service exception
     */
    public void removeUser(final String userId) throws ServiceException {
        final Transaction transaction = userRepository.beginTransaction();

        try {
            userRepository.remove(userId);

            transaction.commit();
        } catch (final RepositoryException e) {
            if (transaction.isActive()) {
                transaction.rollback();
            }

            LOGGER.log(Level.SEVERE, "Removes a user[id=" + userId + "] failed", e);
            throw new ServiceException(e);
        }
    }

    /**
     * Gets the {@link UserMgmtService} singleton.
     *
     * @return the singleton
     */
    public static UserMgmtService getInstance() {
        return SingletonHolder.SINGLETON;
    }

    /**
     * Private constructor.
     */
    private UserMgmtService() {}

    /**
     * Singleton holder.
     *
     * @author <a href="mailto:DL88250@gmail.com">Liang Ding</a>
     * @version 1.0.0.0, Oct 28, 2011
     */
    private static final class SingletonHolder {

        /**
         * Singleton.
         */
        private static final UserMgmtService SINGLETON = new UserMgmtService();

        /**
         * Private default constructor.
         */
        private SingletonHolder() {}
    }
}
