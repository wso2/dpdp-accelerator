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

import { Alert, Button, Snackbar, UserMenu } from '@wso2/oxygen-ui'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getUserProfile, logout } from '../../../utils/authClient'

type UserClaims = Record<string, unknown>

function claim(claims: UserClaims, name: string): string | undefined {
  const value = claims[name]

  return typeof value === 'string' && value.trim() ? value.trim() : undefined
}

function displayName(claims: UserClaims, fallback: string): string {
  const name = claim(claims, 'name') ?? claim(claims, 'displayName')
  if (name) {
    return name
  }

  const fullName = [claim(claims, 'given_name'), claim(claims, 'family_name')]
    .filter(Boolean)
    .join(' ')

  return fullName || claim(claims, 'preferred_username') || claim(claims, 'username') || fallback
}

function UserProfileMenu(): React.JSX.Element {
  const { t } = useTranslation('common')
  const [logoutFailed, setLogoutFailed] = useState(false)
  const [logoutPending, setLogoutPending] = useState(false)
  const [claims] = useState<UserClaims>(() => getUserProfile() ?? {})
  const name = displayName(claims, t('layout.userMenu.unknownUser'))
  const email = claim(claims, 'email') ?? claim(claims, 'sub') ?? t('layout.userMenu.noEmail')
  const avatar = claim(claims, 'picture')

  const handleLogout = async (): Promise<void> => {
    if (logoutPending) {
      return
    }

    setLogoutFailed(false)
    setLogoutPending(true)
    try {
      await logout()
    } catch {
      setLogoutFailed(true)
    } finally {
      setLogoutPending(false)
    }
  }

  return (
    <>
      <UserMenu>
        <UserMenu.Trigger name={name} avatar={avatar} />
        <UserMenu.Header name={name} email={email} avatar={avatar} />
        <UserMenu.Divider />
        <UserMenu.Logout label={t('layout.userMenu.signOut')} onClick={handleLogout} />
      </UserMenu>
      <Snackbar
        open={logoutFailed}
        onClose={() => setLogoutFailed(false)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity="error"
          variant="filled"
          action={
            <Button color="inherit" size="small" disabled={logoutPending} onClick={handleLogout}>
              {t('layout.userMenu.tryAgain')}
            </Button>
          }
        >
          {t('layout.userMenu.signOutError')}
        </Alert>
      </Snackbar>
    </>
  )
}

export default UserProfileMenu
