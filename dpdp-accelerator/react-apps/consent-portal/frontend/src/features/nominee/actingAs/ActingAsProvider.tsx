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

import { useCallback, useMemo, useState } from 'react'
import { stopActingSession } from './actingApi'
import {
  ACTING_STORAGE_KEY,
  ActingAsContext,
  type ActingSession,
  readStoredSession,
} from './actingAsContext'

function ActingAsProvider({ children }: { children: React.ReactNode }): React.JSX.Element {
  const [session, setSession] = useState<ActingSession | null>(readStoredSession)

  const startActing = useCallback((next: ActingSession): void => {
    window.sessionStorage.setItem(ACTING_STORAGE_KEY, JSON.stringify(next))
    setSession(next)
  }, [])

  const stopActing = useCallback((): void => {
    // Clear locally first so the UI leaves acting mode immediately, then ask the
    // BFF to drop the impersonation cookie. If that call fails the token still
    // expires on its own, and every request re-checks the nomination gate
    // regardless - so a failed stop cannot leave usable authority behind.
    window.sessionStorage.removeItem(ACTING_STORAGE_KEY)
    setSession(null)
    stopActingSession().catch(() => undefined)
  }, [])

  const value = useMemo(
    () => ({ session, startActing, stopActing }),
    [session, startActing, stopActing],
  )

  return <ActingAsContext.Provider value={value}>{children}</ActingAsContext.Provider>
}

export default ActingAsProvider
