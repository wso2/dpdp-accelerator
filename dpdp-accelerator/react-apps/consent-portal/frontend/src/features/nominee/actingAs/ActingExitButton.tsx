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

import { Button } from '@wso2/oxygen-ui'
import { LogOut } from '@wso2/oxygen-ui-icons-react'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useActingAs } from './actingAsContext'

/**
 * The way out of an acting session, shown only while one is running.
 *
 * Lives in the header's action area rather than in a bar over the page. A bar
 * across the top travels down over the records being read while scrolling; a
 * floating button covers a corner of them. The header is already reserved for
 * controls, so the exit stays in the same place and in reach on every page
 * without ever sitting on top of the content.
 */
function ActingExitButton(): React.JSX.Element | null {
  const { t } = useTranslation('common')
  const navigate = useNavigate()
  const { session, stopActing } = useActingAs()

  const exitActing = useCallback((): void => {
    stopActing()

    // This view runs in a tab of its own, opened from the nominee's own window.
    // Closing it returns them to where they started, rather than leaving two
    // windows open signed in as two different people - which is how somebody
    // ends up acting for an owner without realising it.
    window.close()

    // A tab the browser will not let a script close - opened by hand, or
    // restored after a reload - stays where it is. Leave acting mode visibly
    // instead of stranding the person on an account they no longer have a
    // session for.
    window.setTimeout(() => {
      navigate('/nominations')
    }, 200)
  }, [navigate, stopActing])

  if (!session) {
    return null
  }

  return (
    <Button
      size="small"
      variant="contained"
      startIcon={<LogOut size={15} />}
      onClick={exitActing}
      sx={{ flex: 'none', mr: 1 }}
    >
      {t('nominee.acting.exit', 'Exit nominee view')}
    </Button>
  )
}

export default ActingExitButton
