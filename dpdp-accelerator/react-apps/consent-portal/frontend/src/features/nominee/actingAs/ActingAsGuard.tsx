/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { Box, Button, Stack, Typography } from '@wso2/oxygen-ui'
import { useTranslation } from 'react-i18next'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useActingAs } from './actingAsContext'
import { canAccessRoute, defaultRouteForScope, isFencedRoute } from './policy'

/**
 * While acting on an owner's account, only allowlisted routes covered by the
 * granted scope render. Anything else — including every account-control area —
 * is refused rather than silently hidden.
 */
function ActingAsGuard(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const location = useLocation()
  const { session, stopActing } = useActingAs()

  if (!session) {
    return <Outlet />
  }

  if (canAccessRoute(location.pathname, session.scope)) {
    return <Outlet />
  }

  const fenced = isFencedRoute(location.pathname)
  const elsewhere = defaultRouteForScope(session.scope)

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={2} alignItems="flex-start" sx={{ maxWidth: 560 }}>
        <Typography variant="h5" fontWeight={700}>
          {t('nominee.acting.blockedTitle', 'Not available in nominee access')}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {fenced
            ? t(
                'nominee.acting.blockedFenced',
                'Account settings, credentials and nominations can never be used on behalf of another person.',
              )
            : t(
                'nominee.acting.blockedScope',
                'The owner did not grant you permission for this area.',
              )}
        </Typography>
        <Stack direction="row" spacing={1}>
          {elsewhere === null ? null : (
            <Button
              variant="contained"
              onClick={() => {
                navigate(elsewhere)
              }}
            >
              {t('nominee.acting.backToAllowed', 'Go to what I can access')}
            </Button>
          )}
          <Button
            variant="outlined"
            onClick={() => {
              stopActing()
              navigate('/nominations')
            }}
          >
            {t('nominee.acting.exit', 'Exit')}
          </Button>
        </Stack>
      </Stack>
    </Box>
  )
}

export default ActingAsGuard
