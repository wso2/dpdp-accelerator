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

import { Box, Stack, Typography } from '@wso2/oxygen-ui'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { exchangeActingSession, readActingCallback } from './actingApi'
import { permissionsFromScopes, useActingAs } from './actingAsContext'
import { defaultRouteForScope } from './policy'

/**
 * Landing point for the Identity Server's impersonation redirect.
 *
 * IS returns the subject token in a URL fragment, which never reaches a server,
 * so this page is the only place it can be read. It is handed straight to the
 * BFF for exchange and then erased from the address bar - it is single-use and
 * short-lived, and leaving it in history would be pointless exposure.
 */
function ActingCallbackPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { startActing } = useActingAs()
  // Captured on first render, before the effect below erases the fragment.
  const [callback] = useState(() => readActingCallback(window.location.hash))
  const [exchangeFailed, setExchangeFailed] = useState(false)
  const attempted = useRef(false)

  const failed = callback === null || exchangeFailed

  useEffect(() => {
    if (!callback || attempted.current) {
      return
    }
    attempted.current = true

    // Drop the fragment before the exchange resolves, so the token is not left
    // sitting in the address bar or in session history.
    window.history.replaceState(null, '', window.location.pathname)

    exchangeActingSession(callback)
      .then((session) => {
        const scope = permissionsFromScopes(session.scopes)
        startActing({
          ownerId: session.ownerId,
          nomineeId: session.nomineeId,
          scope,
          expiresAt: session.expiresAt,
        })
        // Null when the grant covers nothing. Nominations is the one place
        // such a nominee can still go, as themselves.
        navigate(defaultRouteForScope(scope) ?? '/nominations', { replace: true })
      })
      .catch(() => {
        setExchangeFailed(true)
      })
  }, [callback, navigate, startActing])

  return (
    <Box component="main" sx={{ p: { xs: 2, md: 4 } }}>
      <Stack spacing={1}>
        <Typography variant="h6" fontWeight={700}>
          {failed
            ? t('nominee.acting.startFailed', 'You are not a nominee for this account.')
            : t('nominee.acting.starting', 'Opening the account…')}
        </Typography>
        {failed ? (
          <Typography variant="body2" color="text.secondary">
            {t('nominee.acting.startFailedHint', 'Return to Nominations and try again.')}
          </Typography>
        ) : null}
      </Stack>
    </Box>
  )
}

export default ActingCallbackPage
