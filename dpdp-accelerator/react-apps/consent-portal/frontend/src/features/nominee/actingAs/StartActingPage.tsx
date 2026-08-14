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
import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import { canStartActing, redirectToActingStart } from './actingApi'

/**
 * Entry point for nominee access.
 *
 * Hands the browser to the BFF, which redirects on to the Identity Server's
 * impersonation endpoint. The whole exchange has to happen as a real navigation:
 * IS identifies the nominee from its own session cookie and returns the subject
 * token in a URL fragment, neither of which survives an XHR.
 *
 * The nomination gate runs inside IS during that redirect, so this page makes no
 * authorization decision of its own - it only starts the flow.
 */
function StartActingPage(): React.JSX.Element {
  const { t } = useTranslation('common')
  const { ownerId } = useParams<{ ownerId: string }>()
  const attempted = useRef(false)

  // Derived rather than stored: whether the flow can start is a pure function of
  // the route and the environment, so there is no state to keep in sync.
  const failed = !ownerId || !canStartActing()

  useEffect(() => {
    if (failed || !ownerId || attempted.current) {
      return
    }
    attempted.current = true
    redirectToActingStart(ownerId)
  }, [failed, ownerId])

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

export default StartActingPage
